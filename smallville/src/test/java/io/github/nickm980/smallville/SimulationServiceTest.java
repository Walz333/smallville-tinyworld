package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.nickm980.smallville.api.v1.SimulationService;
import io.github.nickm980.smallville.api.v1.dto.CreateAgentRequest;
import io.github.nickm980.smallville.api.v1.dto.CreateLocationRequest;
import io.github.nickm980.smallville.api.v1.dto.CreateMemoryRequest;
import io.github.nickm980.smallville.api.v1.dto.ImportAgentsRequest;
import io.github.nickm980.smallville.api.v1.dto.ImportAgentsResponse;
import io.github.nickm980.smallville.api.v1.dto.SetAgentModelRequest;
import io.github.nickm980.smallville.api.v1.dto.WorldSnapshotResponse;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.WorldProposal;
import io.github.nickm980.smallville.llm.ChatGPT;

public class SimulationServiceTest {

    private SimulationService service;
    private World world;

    @BeforeEach
    public void setUp() {
	ChatGPT llm = Mockito.mock(ChatGPT.class);
	Mockito.when(llm.sendChat(Mockito.any(), Mockito.anyDouble())).thenReturn("result");
	world = new World();
	service = new SimulationService(llm, world);
    }

    @Test
    public void test_location_creation_is_added_to_list() {
	int initialLocationCount = service.getAllLocations().size();

	CreateLocationRequest request = new CreateLocationRequest();
	request.setName("location");

	service.createLocation(request);

	assertEquals(initialLocationCount + 1, service.getAllLocations().size());
    }

    @Test
    public void test_agent_creation_is_added_to_list() {
	int initialAgentCount = service.getAgents().size();

	CreateLocationRequest request = new CreateLocationRequest();
	request.setName("location");

	service.createLocation(request);

	CreateAgentRequest createAgent = new CreateAgentRequest();
	createAgent.setActivity("doing nothing");
	createAgent.setLocation("location");
	createAgent.setMemories(List.of("memory1"));
	createAgent.setName("name");
	createAgent.setTraits("steady, helpful, quiet");

	service.createAgent(createAgent);

	assertEquals(initialAgentCount + 1, service.getAgents().size());
    }

    @Test
    public void test_default_bootstrap_prefers_two_house_scenario_seed() {
	assertEquals(12, service.getAllLocations().size());
	assertEquals(2, service.getAgents().size());
	assertTrue(world.getAgent("Jamie").isPresent());
	assertTrue(world.getAgent("Alex").isPresent());
	assertEquals(
	    "Tea things laid out beside a garden notebook",
	    world.getLocation("Blue House: Kitchen").orElseThrow().getState());
	assertEquals(
	    "Plan sheets and tray maps spread beside a mug ring",
	    world.getLocation("Green House: Glass Table").orElseThrow().getState());
	assertEquals(
	    "Freshly prepared for transplanting and bed tidy work",
	    world.getLocation("Garden: South Bed").orElseThrow().getState());
	assertEquals(
	    "A calm two-house horticultural microcosm built around watering rounds, propagation work, quiet review, and shared tea pauses.",
	    service.getWorldSnapshot().getWorldBuilding().getSummary());
	assertEquals("06:00-09:30", service.getWorldSnapshot().getDailyRhythm().getBreakfast());
    }

    @Test
    public void test_service_memory_creation_does_not_throw() {
	CreateLocationRequest createLocation = new CreateLocationRequest();
	createLocation.setName("location");

	service.createLocation(createLocation);

	CreateAgentRequest createAgent = new CreateAgentRequest();
	createAgent.setActivity("doing nothing");
	createAgent.setLocation("location");
	createAgent.setMemories(List.of("memory1"));
	createAgent.setName("name");
	createAgent.setTraits("steady, helpful, quiet");

	service.createAgent(createAgent);

	CreateMemoryRequest request = new CreateMemoryRequest();
	request.setName("name");
	request.setDescription("description");
	request.setReactable(false);

	assertDoesNotThrow(() -> {
	    service.createMemory(request);
	});
    }

    @Test
    public void test_agent_creation_keeps_persona_and_working_memories_separate() {
	createLocation("Green House: Glass Table");

	CreateAgentRequest createAgent = new CreateAgentRequest();
	createAgent.setActivity("sketching plants");
	createAgent.setLocation("Green House: Glass Table");
	createAgent.setMemories(List.of("Alex is observant", "Alex likes quiet studios"));
	createAgent.setWorkingMemories(List.of("Jamie promised tea", "The Green House needs watering"));
	createAgent.setTraits("curious, artistic, patient");
	createAgent.setName("Morgan");

	service.createAgent(createAgent);

	Agent alex = world.getAgent("Morgan").orElseThrow();

	assertEquals(2, alex.getMemoryStream().getCharacteristics().size());
	assertEquals(2, alex.getMemoryStream().getWorkingMemories().size());
	assertEquals(0, alex.getMemoryStream().getObservations().size());
	assertEquals("curious, artistic, patient", alex.getTraits());
    }

    @Test
    public void test_world_snapshot_serializes_with_agents_and_locations() {
	createLocation("Green House");
	createLocation("Green House: Glass Table");

	CreateAgentRequest createAgent = new CreateAgentRequest();
	createAgent.setActivity("sketching plans");
	createAgent.setLocation("Green House: Glass Table");
	createAgent.setMemories(List.of("Alex likes quiet studios"));
	createAgent.setWorkingMemories(List.of("Jamie promised tea"));
	createAgent.setTraits("curious, artistic, patient");
	createAgent.setName("Taylor");
	service.createAgent(createAgent);

	assertDoesNotThrow(() -> {
	    new ObjectMapper().writeValueAsString(service.getWorldSnapshot());
	});
	assertEquals("06:00-09:30", service.getWorldSnapshot().getDailyRhythm().getBreakfast());
    }

    @Test
    public void test_world_snapshot_exposes_distinct_working_and_recent_memories() {
	createLocation("Green House: Glass Table");

	CreateAgentRequest createAgent = new CreateAgentRequest();
	createAgent.setActivity("sketching plans");
	createAgent.setLocation("Green House: Glass Table");
	createAgent.setMemories(List.of("Alex likes quiet studios"));
	createAgent.setWorkingMemories(List.of("Jamie promised tea"));
	createAgent.setTraits("curious, artistic, patient");
	createAgent.setName("Taylor");
	service.createAgent(createAgent);

	CreateMemoryRequest request = new CreateMemoryRequest();
	request.setName("Taylor");
	request.setDescription("watered the propagation tray");
	request.setReactable(false);
	service.createMemory(request);

	WorldSnapshotResponse.WorldAgentResponse snapshotAgent = service.getWorldSnapshot().getAgents().stream()
	    .filter(agent -> agent.getName().equals("Taylor"))
	    .findFirst()
	    .orElseThrow();

	assertEquals(List.of("Jamie promised tea"), snapshotAgent.getWorkingMemories());
	assertEquals(List.of("watered the propagation tray"), snapshotAgent.getRecentMemories());
	assertFalse(snapshotAgent.getWorkingMemories().equals(snapshotAgent.getRecentMemories()));
	assertTrue(snapshotAgent.getRecentMemories().contains("watered the propagation tray"));
	assertFalse(snapshotAgent.getRecentMemories().contains("Jamie promised tea"));
    }

    @Test
    public void test_model_override_is_reflected_in_world_snapshot() {
	createLocation("Green House: Glass Table");

	CreateAgentRequest createAgent = new CreateAgentRequest();
	createAgent.setActivity("sketching plans");
	createAgent.setLocation("Green House: Glass Table");
	createAgent.setMemories(List.of("Alex likes quiet studios"));
	createAgent.setName("Taylor");
	createAgent.setModel("gwen2.5:0.5b");
	createAgent.setTraits("curious, artistic, patient");
	service.createAgent(createAgent);

	SetAgentModelRequest override = new SetAgentModelRequest();
	override.setModel("llama3.2:3b");
	service.setAgentModel("Taylor", override);

	assertEquals(
	    "llama3.2:3b",
	    service.getWorldSnapshot().getAgents().stream()
		.filter(agent -> agent.getName().equals("Taylor"))
		.findFirst()
		.orElseThrow()
		.getModel());
    }

    @Test
    public void test_import_agents_preview_reports_missing_location() {
	ImportAgentsRequest request = new ImportAgentsRequest();
	request.setPreview(true);
	request.setYaml("agents:\n  - name: Rowan\n    location: Missing Place\n    activity: checking seeds\n    persona:\n      - Rowan keeps notes");

	ImportAgentsResponse response = service.importAgents(request);

	assertEquals(false, response.isSuccess());
	assertEquals(1, response.getAgents().size());
	assertEquals(true, response.getErrors().get(0).contains("Location does not exist"));
    }

    @Test
    public void test_memory_stream_add_and_query_preserves_duplicate_relevant_memories() {
	UUID uuid = service.createMemoryStream();
	assertTrue(service.addMemory(uuid, "memory 1"));
	assertTrue(service.addMemory(uuid, "memory 1"));
	assertTrue(service.addMemory(uuid, "memory 1"));
	assertTrue(service.addMemory(uuid, "memory 1"));

	List<String> memories = service.getMemories(uuid, "memory 1");

	assertEquals(3, memories.size());
	assertEquals(List.of("memory 1", "memory 1", "memory 1"), memories);
    }

    @Test
    public void test_add_location_accepts_container_like_parents() throws Exception {
	createLocation("Green House");
	createLocation("Green House: Potting Room");
	createLocation("Garden");

	assertTrue(isProposalValid(newProposal("add_location", "Garden", "North Arbor", null, "To make watering rounds easier to follow.")));
	assertTrue(isProposalValid(newProposal("add_location", "Green House", "Tray Room", null, "To separate propagation work from the main table.")));
	assertTrue(isProposalValid(newProposal("add_location", "Green House: Potting Room", "Seed Nook", null, "To give note-making and seed sorting a quiet corner.")));
    }

    @Test
    public void test_add_location_rejects_object_like_leaf_parents() throws Exception {
	createLocation("Green House");
	createLocation("Green House: Water Barrel");
	createLocation("Green House: Glass Table");
	createLocation("Garden");
	createLocation("Garden: Bench");

	assertFalse(isProposalValid(newProposal("add_location", "Green House: Water Barrel", "Rain Barrel Alcove", null, "To keep spare barrels together.")));
	assertFalse(isProposalValid(newProposal("add_location", "Green House: Glass Table", "Tool Caddy Corner", null, "To keep cuttings paperwork nearby.")));
	assertFalse(isProposalValid(newProposal("add_location", "Garden: Bench", "Pause Shelf", null, "To give tea cups a tidy resting place.")));
    }

    @Test
    public void test_valid_pending_add_location_remains_visible_for_review() throws Exception {
	createLocation("Garden");

	WorldProposal proposal = newProposal("add_location", "Garden", "North Arbor", "ready", "To keep the watering route organized.");
	assertTrue(isProposalValid(proposal));

	addPendingProposal(proposal);

	assertEquals(1, service.getWorldProposals().size());
	assertEquals("add_location", service.getWorldProposals().get(0).getType());
	assertEquals("Garden", service.getWorldProposals().get(0).getParentLocation());
	assertEquals("North Arbor", service.getWorldProposals().get(0).getName());
	assertEquals("To keep the watering route organized.", service.getWorldProposals().get(0).getReason());
	assertEquals("pending", service.getWorldProposals().get(0).getStatus());
    }

    @Test
    public void test_non_add_location_proposal_types_keep_previous_behavior() throws Exception {
	createLocation("Green House");
	createLocation("Green House: Glass Table");

	assertTrue(isProposalValid(newProposal("add_object", "Green House: Glass Table", "Tool Basket", null, "To keep twine and labels together.")));
	assertTrue(isProposalValid(newProposal("change_state", null, "Green House: Glass Table", "cleared", "To mark the work surface ready for propagation.")));
    }

    @Test
    public void test_duplicate_pending_add_location_is_still_rejected() throws Exception {
	createLocation("Garden");

	WorldProposal first = newProposal("add_location", "Garden", "North Arbor", null, "To keep the watering route organized.");
	assertTrue(isProposalValid(first));
	addPendingProposal(first);

	WorldProposal duplicate = newProposal("add_location", "Garden", "North Arbor", null, "To keep the watering route organized.");
	assertFalse(isProposalValid(duplicate));
    }

    private void createLocation(String name) {
	if (world.getLocation(name).isPresent()) {
	    return;
	}

	CreateLocationRequest request = new CreateLocationRequest();
	request.setName(name);
	service.createLocation(request);
    }

    private WorldProposal newProposal(String type, String parentLocation, String name, String proposedState, String reason) {
	WorldProposal proposal = new WorldProposal();
	proposal.setId(UUID.randomUUID().toString());
	proposal.setAgent("Jamie");
	proposal.setType(type);
	proposal.setParentLocation(parentLocation);
	proposal.setName(name);
	proposal.setProposedState(proposedState);
	proposal.setReason(reason);
	proposal.setStatus("pending");
	proposal.setCreatedAtTick(0);
	return proposal;
    }

    private boolean isProposalValid(WorldProposal proposal) throws Exception {
	Method method = SimulationService.class.getDeclaredMethod("isProposalValid", WorldProposal.class);
	method.setAccessible(true);
	return (boolean) method.invoke(service, proposal);
    }

    @SuppressWarnings("unchecked")
    private void addPendingProposal(WorldProposal proposal) throws Exception {
	Field field = SimulationService.class.getDeclaredField("proposals");
	field.setAccessible(true);
	List<WorldProposal> proposals = (List<WorldProposal>) field.get(service);
	assertNotNull(proposals);
	proposals.add(proposal);
    }
}

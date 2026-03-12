package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

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
import io.github.nickm980.smallville.entities.Agent;
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
	CreateLocationRequest request = new CreateLocationRequest();
	request.setName("location");

	service.createLocation(request);

	assertEquals(1, service.getAllLocations().size());
    }

    @Test
    public void test_agent_creation_is_added_to_list() {
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

	assertEquals(1, service.getAgents().size());
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
	CreateLocationRequest createLocation = new CreateLocationRequest();
	createLocation.setName("Green House: Glass Table");

	service.createLocation(createLocation);

	CreateAgentRequest createAgent = new CreateAgentRequest();
	createAgent.setActivity("sketching plants");
	createAgent.setLocation("Green House: Glass Table");
	createAgent.setMemories(List.of("Alex is observant", "Alex likes quiet studios"));
	createAgent.setWorkingMemories(List.of("Jamie promised tea", "The Green House needs watering"));
	createAgent.setTraits("curious, artistic, patient");
	createAgent.setName("Alex");

	service.createAgent(createAgent);

	Agent alex = world.getAgent("Alex").orElseThrow();

	assertEquals(2, alex.getMemoryStream().getCharacteristics().size());
	assertEquals(2, alex.getMemoryStream().getObservations().size());
	assertEquals("curious, artistic, patient", alex.getTraits());
    }

    @Test
    public void test_world_snapshot_serializes_with_agents_and_locations() {
	CreateLocationRequest root = new CreateLocationRequest();
	root.setName("Green House");
	service.createLocation(root);

	CreateLocationRequest leaf = new CreateLocationRequest();
	leaf.setName("Green House: Glass Table");
	service.createLocation(leaf);

	CreateAgentRequest createAgent = new CreateAgentRequest();
	createAgent.setActivity("sketching plans");
	createAgent.setLocation("Green House: Glass Table");
	createAgent.setMemories(List.of("Alex likes quiet studios"));
	createAgent.setWorkingMemories(List.of("Jamie promised tea"));
	createAgent.setTraits("curious, artistic, patient");
	createAgent.setName("Alex");
	service.createAgent(createAgent);

	assertDoesNotThrow(() -> {
	    new ObjectMapper().writeValueAsString(service.getWorldSnapshot());
	});
	assertEquals("06:00-09:30", service.getWorldSnapshot().getDailyRhythm().getBreakfast());
    }

    @Test
    public void test_model_override_is_reflected_in_world_snapshot() {
	CreateLocationRequest request = new CreateLocationRequest();
	request.setName("Green House: Glass Table");
	service.createLocation(request);

	CreateAgentRequest createAgent = new CreateAgentRequest();
	createAgent.setActivity("sketching plans");
	createAgent.setLocation("Green House: Glass Table");
	createAgent.setMemories(List.of("Alex likes quiet studios"));
	createAgent.setName("Alex");
	createAgent.setModel("gwen2.5:0.5b");
	createAgent.setTraits("curious, artistic, patient");
	service.createAgent(createAgent);

	SetAgentModelRequest override = new SetAgentModelRequest();
	override.setModel("llama3.2:3b");
	service.setAgentModel("Alex", override);

	assertEquals("llama3.2:3b", service.getWorldSnapshot().getAgents().get(0).getModel());
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
}

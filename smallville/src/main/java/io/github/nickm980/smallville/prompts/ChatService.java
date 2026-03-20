package io.github.nickm980.smallville.prompts;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nickm980.smallville.Util;
import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.config.SmallvilleConfig;
import io.github.nickm980.smallville.config.simulation.SimulationFile;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Conversation;
import io.github.nickm980.smallville.entities.Dialog;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.memory.Memory;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.memory.Reflection;
import io.github.nickm980.smallville.nlp.LocalNLP;
import io.github.nickm980.smallville.prompts.dto.CurrentActivity;
import io.github.nickm980.smallville.prompts.dto.ObjectChangeResponse;
import io.github.nickm980.smallville.prompts.dto.Reaction;
import io.github.nickm980.smallville.prompts.dto.WorldProposalCandidate;
import io.github.nickm980.smallville.runtime.RuntimeSettingsService;
import io.github.nickm980.smallville.update.UpdateService;

public class ChatService implements Prompts {
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b\\d{1,2}:\\d{2}\\s*[APap][Mm]\\b");
    private static final Pattern PLAN_PREFIX_PATTERN = Pattern.compile("^(?:[-*•]\\s+|\\.\\s+|\\d+[.)]\\s+)+");
    private static final Pattern TIME_ONLY_PATTERN = Pattern.compile("^\\d{1,2}:\\d{2}\\s*[APap][Mm]\\.?$");

    private final LLM chat;
    private final static Logger LOG = LoggerFactory.getLogger(UpdateService.class);
    private final World world;
    private final SimulationFile simulationSeed;
    private final RuntimeSettingsService runtimeSettings;

    public ChatService(World world, LLM chat) {
	this(world, chat, null, new RuntimeSettingsService(SmallvilleConfig.getConfig()));
    }

    public ChatService(World world, LLM chat, SimulationFile simulationSeed, RuntimeSettingsService runtimeSettings) {
	this.chat = chat;
	this.world = world;
	this.simulationSeed = simulationSeed;
	this.runtimeSettings = runtimeSettings;
    }

    private PromptBuilder builder() {
	return new PromptBuilder()
	    .withDailyRhythm(getDailyRhythm())
	    .withWorldBuilding(simulationSeed == null ? null : simulationSeed.getWorldBuilding());
    }

    private SimulationFile.DailyRhythmSeed getDailyRhythm() {
	if (simulationSeed != null && simulationSeed.getDailyRhythm() != null) {
	    return simulationSeed.getDailyRhythm();
	}

	return new SimulationFile.DailyRhythmSeed();
    }

    private PromptRequest buildPrompt(Agent agent, PromptBuilder builder, String promptText) {
	PromptRequest request = builder.setPrompt(promptText).build();
	if (agent != null) {
	    request.setModel(runtimeSettings.resolveModel(agent.getFullName(), agent.getModel()));
	}
	return request;
    }

    @Override
    public int[] getWeights(Agent agent) {
	PromptRequest prompt = buildPrompt(agent, builder()
	    .withAgent(agent)
	    , SmallvilleConfig.getPrompts().getMisc().getRankMemories());

	String response = chat.sendChat(prompt, .1);
	response = response.replace(",]", "]");

	ObjectMapper objectMapper = new ObjectMapper();
	int[] result = new int[0];

	if (!response.contains("[")) {
	    result = new int[1];
	    result[0] = Integer.parseInt(response);
	    return result;
	}

	try {
	    result = objectMapper.readValue(response, int[].class);
	} catch (JsonProcessingException e) {
	    LOG.error("Failed to parse json for memory ranking. Continuing anyways...");
	}

	return result;
    }

    @Override
    public String ask(Agent agent, String question) {
	PromptRequest prompt = buildPrompt(agent, builder()
	    .withObservation(question.replace("?", ""))
	    .withQuestion(question)
	    .withLocations(world.getLocations())
	    .withAgent(agent)
	    , SmallvilleConfig.getPrompts().getAgent().getAskQuestion());

	return chat.sendChat(prompt, .5);
    }

    @Override
    public List<Plan> getPlans(Agent agent) {
	PromptRequest prompt = buildPrompt(agent, builder()
	    .withLocations(world.getLocations())
	    .withObservation(agent.getMemoryStream().getLastObservation().getDescription())
	    .withAgent(agent)
	    .withWorld(world, simulationSeed)
	    , SmallvilleConfig.getPrompts().getPlans().getLongTerm());

	String response = chat.sendChat(prompt, .6);
	return parsePlans(response);
    }

    @Override
    public List<Plan> getShortTermPlans(Agent agent) {
	PromptRequest prompt = buildPrompt(agent, builder()
	    .withLocations(world.getLocations())
	    .withObservation(agent.getMemoryStream().getLastObservation().getDescription())
	    .withAgent(agent)
	    .withWorld(world, simulationSeed)
	    , SmallvilleConfig.getPrompts().getPlans().getShortTerm());

	String response = chat.sendChat(prompt, .7);

	return parsePlans(response);
    }

    @Override
    public CurrentActivity getCurrentActivity(Agent agent) {
	PromptRequest prompt = buildPrompt(agent, builder()
	    .withAgent(agent)
	    .withWorld(world, simulationSeed)
	    .withLocations(world.getLocations())
	    , SmallvilleConfig.getPrompts().getPlans().getCurrent());

	String response = chat.sendChat(prompt, .5);

	LocalNLP nlp = new LocalNLP();
	CurrentActivity activity = Util.parseAsClass(response, CurrentActivity.class);
	if (activity == null) {
	    activity = new CurrentActivity();
	}
	LOG.info("{} {}", activity.getActivity(), activity.getLocation());
	activity.setLastActivity(nlp.convertToPastTense(agent.getCurrentActivity()));
	activity.setActivity(normalizeMealLanguage(activity.getActivity(), SimulationTime.now()));

	return activity;
    }

    @Override
    public Conversation getConversationIfExists(Agent agent, Agent other, String topic) {
	PromptRequest prompt = buildPrompt(agent, builder()
	    .withAgent(agent)
	    .withOther(other)
	    .withObservation(topic)
	    , SmallvilleConfig.getPrompts().getReactions().getConversation());

	String response = chat.sendChat(prompt, .7);
	if (response == null || response.isBlank()) {
	    LOG.warn("Conversation response was empty for {} and {}", agent.getFullName(), other.getFullName());
	    return new Conversation(agent.getFullName(), other.getFullName(), List.of());
	}
	String[] lines = response.split("\\r?\\n");

	List<Dialog> dialogs = new ArrayList<>();
	for (String line : lines) {
	    String[] parts = line.split(":\\s+", 2);
	    if (parts.length == 2) { // ignores all lines before the conversation
		dialogs.add(new Dialog(parts[0], parts[1]));
	    }
	}

	Conversation conversation = new Conversation(agent.getFullName(), other.getFullName(), dialogs);
	return conversation;
    }

    @Override
    public List<Plan> parsePlans(String input) {
	List<Plan> plans = new ArrayList<>();

	for (String line : extractPlanSegments(input)) {
	    LocalDateTime start = null;

	    try {
		start = parseTime(line);
	    } catch (Exception e) {
		LOG.error("Could not parse time");
		continue;
	    }

	    if (start == null) {
		continue;
	    }

	    Plan plan = new Plan(normalizeMealLanguage(line.trim(), start), start);
	    plans.add(plan);
	}

	return plans;
    }

    private List<String> extractPlanSegments(String input) {
	List<String> segments = new ArrayList<String>();
	if (input == null || input.isBlank()) {
	    return segments;
	}

	for (String rawLine : input.replace("\r", "").split("\n")) {
	    String line = normalizePlanSegment(rawLine);
	    if (line.isBlank()) {
		continue;
	    }

	    if (TIME_ONLY_PATTERN.matcher(line).matches()) {
		continue;
	    }

	    segments.add(line);
	}

	return segments;
    }

    private String normalizePlanSegment(String rawLine) {
	if (rawLine == null) {
	    return "";
	}

	String normalized = rawLine.trim();
	normalized = PLAN_PREFIX_PATTERN.matcher(normalized).replaceFirst("");
	normalized = normalized.replaceFirst("^[;,-]+\\s*", "");
	return normalized.trim();
    }

    private LocalDateTime parseTime(String line) throws DateTimeParseException {
	if (line.isBlank()) {
	    return null;
	}

	Matcher matcher = TIME_PATTERN.matcher(line);
	if (!matcher.find()) {
	    LOG.warn("Temporal memory possibly missing a time. " + line);
	    return null;
	}

	String time = matcher.group().trim().toUpperCase(Locale.ENGLISH).replaceAll("\\s+", " ");
	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

	return LocalDateTime.of(SimulationTime.now().toLocalDate(), LocalTime.parse(time, formatter));
    }

    @Override
    public ObjectChangeResponse[] getObjectsChangedBy(Agent agent) {
	if (agent.getCurrentActivity().equals(agent.getLastActivity())) {
	    return new ObjectChangeResponse[0];
	}

	PromptRequest tensesPrompt = buildPrompt(agent, builder()
	    .withAgent(agent)
	    .withWorld(world, simulationSeed)
	    , SmallvilleConfig.getPrompts().getMisc().getCombineSentences()); // might be able to use LocalNLP for this

	String tenses = chat.sendChat(tensesPrompt, .1);

	PromptRequest changedPrompt = buildPrompt(agent, builder()
	    .withAgent(agent)
	    .withTense(tenses)
	    .withWorld(world, simulationSeed)
	    .withLocations(world.getLocations())
	    , SmallvilleConfig.getPrompts().getWorld().getObjectStates());

	String response = chat.sendChat(changedPrompt, .3);

	String[] lines = response.split("\n");
	ObjectChangeResponse[] objects = new ObjectChangeResponse[lines.length];

	for (int i = 0; i < lines.length; i++) {
	    String line = lines[i];

	    if (line.isBlank()) {
		continue;
	    }

	    String[] parts = line.split(":");

	    if (parts.length < 2) {
		continue;
	    }

	    String item = parts[0].trim();
	    String value = parts[1].trim();

	    LOG.debug("Trying to change " + item + " to " + value);

	    if (item != null && value != null && !value.equalsIgnoreCase("Unchanged")) {
		objects[i] = new ObjectChangeResponse(item, value);
	    }
	}

	if (objects.length == 0) {
	    LOG.warn("No objects were updated");
	}

	return objects;
    }

    @Override
    public Reflection createReflectionFor(Agent agent) {
	Reflection reflection = new Reflection("");
	PromptRequest prompt = buildPrompt(agent, builder()
	    .withAgent(agent)
	    , SmallvilleConfig.getPrompts().getAgent().getReflectionQuestion());

	String query = chat.sendChat(prompt, .1);
	String[] lines = query.split("\n");
	query = query.split("\n")[lines.length - 1].substring(2);

	LOG.debug("[Reflections] Question: " + query);

	Set<Memory> filter = new HashSet<Memory>();
	filter.addAll(agent.getMemoryStream().getRelevantMemories(query));
	List<Memory> memories = new ArrayList<>(filter); // Convert the set back to a list

	LOG.debug(String.join(",", memories.stream().map(m -> m.getDescription()).collect(Collectors.toList())));

	PromptRequest secondPrompt = buildPrompt(agent, builder()
	    .withAgent(agent)
	    .withStatements(memories.stream().map(m -> m.getDescription()).collect(Collectors.toList()))
	    , SmallvilleConfig.getPrompts().getAgent().getReflectionResult());

	String description = chat.sendChat(secondPrompt, .8);

	// retrieve just the insight. remove the because clause and the key
	int index = description.lastIndexOf(":");

	if (index != -1) {
	    description = description.substring(index);
	}

	description = description.replaceAll(":", "").trim();

	reflection.setDescription(description);

	return reflection;
    }

    @Override
    public Reaction shouldUpdatePlans(Agent agent, String observation) {
	PromptRequest prompt = buildPrompt(agent, builder()
	    .withObservation(observation)
	    .withAgent(agent)
	    , SmallvilleConfig.getPrompts().getReactions().getReaction());

	String response = chat.sendChat(prompt, .2);
	Reaction result = Util.parseAsClass(response, Reaction.class);
	if (result == null) {
	    result = new Reaction();
	    result.setAnswer("No");
	    result.setConversation("No");
	}
	if (result.getAnswer() == null) {
	    result.setAnswer("No");
	}
	if (result.getConversation() == null) {
	    result.setConversation("No");
	}

	LOG.debug("reacting " + result.getAnswer());
	return result;
    }

    public String createTraits(Agent agent) {
	PromptRequest prompt = buildPrompt(agent, builder()
	    .withAgent(agent)
	    , SmallvilleConfig.getPrompts().getAgent().getCharacteristics());

	return chat.sendChat(prompt, .5);
    }

    @Override
    public Dialog saySomething(Agent agent, String observation) {
	PromptRequest request = buildPrompt(agent, builder()
	    .withObservation(observation)
	    .withAgent(agent)
	    , SmallvilleConfig.getPrompts().getReactions().getSay());
	
	String result = chat.sendChat(request, .5);
	
	return new Dialog(agent.getFullName(), result);
    }

    public WorldProposalCandidate createWorldProposal(Agent agent) {
	if (SmallvilleConfig.getPrompts().getWorld().getProposal() == null
		|| SmallvilleConfig.getPrompts().getWorld().getProposal().isBlank()) {
	    return null;
	}

	PromptRequest request = buildPrompt(agent, builder()
	    .withAgent(agent)
	    .withWorld(world, simulationSeed)
	    .withLocations(world.getLocations())
	    , SmallvilleConfig.getPrompts().getWorld().getProposal());

	String response = chat.sendChat(request, .4);
	WorldProposalCandidate candidate = parseWorldProposal(response);
	if (candidate == null) {
	    return null;
	}

	return candidate;
    }

    private WorldProposalCandidate parseWorldProposal(String response) {
	if (response == null || response.isBlank()) {
	    return null;
	}

	WorldProposalCandidate candidate = new WorldProposalCandidate();
	Util.parseCson(response).forEach((key, value) -> {
	    String normalizedKey = key.trim().toLowerCase(Locale.ENGLISH);
	    if ("answer".equals(normalizedKey)) {
		candidate.setAnswer(value);
	    } else if ("type".equals(normalizedKey)) {
		candidate.setType(value);
	    } else if ("parentlocation".equals(normalizedKey) || "parent_location".equals(normalizedKey)) {
		candidate.setParentLocation(value);
	    } else if ("name".equals(normalizedKey)) {
		candidate.setName(value);
	    } else if ("proposedstate".equals(normalizedKey) || "proposed_state".equals(normalizedKey)) {
		candidate.setProposedState(value);
	    } else if ("reason".equals(normalizedKey)) {
		candidate.setReason(value);
	    }
	});

	return candidate.getType() == null && candidate.getName() == null && candidate.getReason() == null ? null : candidate;
    }

    private String normalizeMealLanguage(String text, LocalDateTime time) {
	if (text == null || text.isBlank() || time == null) {
	    return text;
	}

	String lowered = text.toLowerCase(Locale.ENGLISH);
	int hour = time.getHour();

	if (hour < 11) {
	    if (lowered.contains("dinner")) {
		return text.replaceAll("(?i)dinner", "breakfast");
	    }
	    if (lowered.contains("lunch")) {
		return text.replaceAll("(?i)lunch", "breakfast");
	    }
	}

	if (hour >= 11 && hour < 15 && lowered.contains("dinner")) {
	    return text.replaceAll("(?i)dinner", "lunch");
	}

	if (hour >= 15 && hour < 18 && lowered.contains("breakfast")) {
	    return text.replaceAll("(?i)breakfast", "snack");
	}

	return text;
    }
}

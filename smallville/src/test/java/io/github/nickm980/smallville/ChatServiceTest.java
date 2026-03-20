package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.memory.Characteristic;
import io.github.nickm980.smallville.memory.MemoryStream;
import io.github.nickm980.smallville.memory.Observation;
import io.github.nickm980.smallville.memory.Reflection;
import io.github.nickm980.smallville.prompts.ChatService;
import io.github.nickm980.smallville.prompts.dto.CurrentActivity;
import io.github.nickm980.smallville.update.UpdateService;

public class ChatServiceTest {

    @Test
    public void test_get_current_activity_logs_activity_and_location_with_separator() {
	World world = new World();
	Location kitchen = new Location("Blue House: Kitchen");
	world.create(kitchen);

	Agent agent = new Agent(
	    "Jamie",
	    List.of(new Characteristic("grounded"), new Characteristic("hospitable")),
	    "brewing tea",
	    kitchen);

	LLM llm = Mockito.mock(LLM.class);
	Mockito.when(llm.sendChat(Mockito.any(), Mockito.anyDouble())).thenReturn(
	    "Activity: bringing tea to Alex\n"
		+ "Location: Green House: Glass Table\n"
		+ "Emoji: tea");

	ChatService service = new ChatService(world, llm);
	Logger logger = Logger.getLogger(UpdateService.class);
	CapturingAppender appender = new CapturingAppender();
	logger.addAppender(appender);

	try {
	    CurrentActivity result = service.getCurrentActivity(agent);

	    assertEquals("bringing tea to Alex", result.getActivity());
	    assertEquals("Green House: Glass Table", result.getLocation());
	    assertEquals("tea", result.getEmoji());
	    assertTrue(appender.messages.stream().anyMatch(message -> message.equals("bringing tea to Alex Green House: Glass Table")));
	    assertFalse(appender.messages.stream().anyMatch(message -> message.contains("bringing tea to AlexGreen House: Glass Table")));
	} finally {
	    logger.removeAppender(appender);
	}
    }

    @Test
    public void test_get_current_activity_strips_exact_duplicate_location_suffix() {
	World world = new World();
	Location kitchen = new Location("Blue House: Kitchen");
	world.create(kitchen);

	Agent agent = new Agent(
	    "Jamie",
	    List.of(new Characteristic("grounded"), new Characteristic("hospitable")),
	    "checking on seedlings",
	    kitchen);

	LLM llm = Mockito.mock(LLM.class);
	Mockito.when(llm.sendChat(Mockito.any(), Mockito.anyDouble())).thenReturn(
	    "Activity: checking on seedlings at Blue House: Kitchen\n"
		+ "Location: Blue House: Kitchen\n"
		+ "Emoji: sprout");

	ChatService service = new ChatService(world, llm);

	CurrentActivity result = service.getCurrentActivity(agent);

	assertEquals("checking on seedlings", result.getActivity());
	assertEquals("Blue House: Kitchen", result.getLocation());
	assertEquals("sprout", result.getEmoji());
    }

    @Test
    public void test_get_current_activity_preserves_non_duplicate_location_wording() {
	World world = new World();
	Location kitchen = new Location("Blue House: Kitchen");
	world.create(kitchen);

	Agent agent = new Agent(
	    "Jamie",
	    List.of(new Characteristic("grounded"), new Characteristic("hospitable")),
	    "checking on seedlings",
	    kitchen);

	LLM llm = Mockito.mock(LLM.class);
	Mockito.when(llm.sendChat(Mockito.any(), Mockito.anyDouble())).thenReturn(
	    "Activity: checking on seedlings beside Blue House: Kitchen\n"
		+ "Location: Blue House: Kitchen\n"
		+ "Emoji: sprout");

	ChatService service = new ChatService(world, llm);

	CurrentActivity result = service.getCurrentActivity(agent);

	assertEquals("checking on seedlings beside Blue House: Kitchen", result.getActivity());
	assertEquals("Blue House: Kitchen", result.getLocation());
	assertEquals("sprout", result.getEmoji());
    }

    @Test
    public void test_create_reflection_for_uses_once_trimmed_question_for_relevant_memories() throws Exception {
	World world = new World();
	Location kitchen = new Location("Blue House: Kitchen");
	world.create(kitchen);

	Agent agent = new Agent(
	    "Jamie",
	    List.of(new Characteristic("grounded"), new Characteristic("hospitable")),
	    "brewing tea",
	    kitchen);

	MemoryStream memories = Mockito.spy(new MemoryStream());
	Field field = Agent.class.getDeclaredField("memories");
	field.setAccessible(true);
	field.set(agent, memories);

	Mockito.doReturn(List.of(new Observation("Alex and Jamie are active around that time")))
	    .when(memories)
	    .getRelevantMemories(ArgumentMatchers.anyString());

	LLM llm = Mockito.mock(LLM.class);
	Mockito.when(llm.sendChat(Mockito.any(), Mockito.anyDouble())).thenReturn(
	    "- What should Jamie tend next?\n"
		+ "- Who needs tea next?\n"
		+ "- At 8:50 am) and seem to be active around that time",
	    "Insight: Jamie notices a shared morning rhythm");

	ChatService service = new ChatService(world, llm);

	Reflection reflection = service.createReflectionFor(agent);

	Mockito.verify(memories).getRelevantMemories("At 8:50 am) and seem to be active around that time");
	Mockito.verify(memories, Mockito.never()).getRelevantMemories(" 8:50 am) and seem to be active around that time");
	assertEquals("Jamie notices a shared morning rhythm", reflection.getDescription());
    }

    private static class CapturingAppender extends AppenderSkeleton {
	private final List<String> messages = new ArrayList<String>();

	@Override
	protected void append(LoggingEvent event) {
	    messages.add(String.valueOf(event.getRenderedMessage()));
	}

	@Override
	public void close() {
	}

	@Override
	public boolean requiresLayout() {
	    return false;
	}
    }
}

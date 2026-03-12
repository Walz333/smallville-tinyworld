package io.github.nickm980.smallville.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

/**
 * Includes plans, observations, and characteristics
 */
public class MemoryStream {
    private List<Memory> memories;

    public MemoryStream() {
	this.memories = new ArrayList<Memory>();
    }

    public void prunePlans(PlanType type) {
	memories.removeIf(memory -> memory instanceof Plan && ((Plan) memory).getType() == type);
    }

    public List<Memory> getRelevantMemories(String query) {
	int defaultMinImportance = 0;

	return getRelevantMemories(query, defaultMinImportance);
    }

    /**
     * Prunes the weaker, less poingnant memories and returns the strongest ones
     * based on observations and updated plans.
     * <p>
     * Will run several comparisons. First, will extract names from the query and
     * compare the token embeddings of the names to each memory. Then will do the
     * same for the full query.
     * 
     * @return
     */
    public List<Memory> getRelevantMemories(String query, int minImportance) {
	return memories
	    .stream()
	    .filter(memory -> memory.getImportance() >= minImportance)
	    .sorted(Comparator.comparingDouble((Memory memory) -> memory.getScore(query)).reversed())
	    .limit(3)
	    .collect(Collectors.toList());
    }

    public List<Memory> getUnweightedMemories() {
	return memories.stream().filter(memory -> {
	    return memory.getImportance() == 0 && !(memory instanceof Plan);
	}).collect(Collectors.toList());
    }

    public double sumRecency() {
	return getRecentMemories().stream().flatMapToDouble(memory -> DoubleStream.of(memory.getImportance())).sum();
    }

    public List<Memory> getRecentMemories() {
	List<Memory> result = memories
	    .stream()
	    .filter(memory -> memory.getRecency() > .4 && !(memory instanceof Plan))
	    .collect(Collectors.toList());
	return result;
    }

    public List<Memory> getMemories() {
	return memories;
    }

    public List<Observation> getObservations() {
	return filterMemoriesByType(Observation.class).collect(Collectors.toList());
    }

    public List<Characteristic> getCharacteristics() {
	return filterMemoriesByType(Characteristic.class).collect(Collectors.toList());
    }

    public List<Plan> getPlans() {
	return filterMemoriesByType(Plan.class).sorted(new TemporalMemory.TemporalComparator())
		.collect(Collectors.toList());
    }

    private <T extends Memory> Stream<T> filterMemoriesByType(Class<T> memoryType) {
	return memories.stream().filter(memoryType::isInstance).map(memoryType::cast);
    }

    public void addAll(List<? extends Memory> memories) {
	this.memories.addAll(memories);
    }

    public void add(Memory memory) {
	this.memories.add(memory);
    }

    public void setPlans(List<Plan> plans, PlanType type) {
	List<Plan> removed = getPlans(type);
	memories.removeAll(removed);
	memories.addAll(plans);
    }

    public List<? extends TemporalMemory> sortByTime(List<? extends TemporalMemory> mems) {
	return mems.stream().sorted(new Comparator<TemporalMemory>() {
	    @Override
	    public int compare(TemporalMemory o1, TemporalMemory o2) {
		return o1.getTime().compareTo(o2.getTime());
	    }
	}).collect(Collectors.toList());
    }

    public List<Plan> getPlans(PlanType term) {
	return getPlans().stream().filter(plan -> plan.getType() == term).collect(Collectors.toList());
    }

    public Observation getLastObservation() {
	List<Observation> observations = getObservations();

	if (observations == null || observations.isEmpty()) {
	    return new Observation("");
	}

	return observations.get(observations.size() - 1);
    }
}

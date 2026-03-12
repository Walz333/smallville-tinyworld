package io.github.nickm980.smallville.prompts.dto;

import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.config.simulation.SimulationFile;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Location;

public class WorldModel {

    private String description;

    public String getDescription() {
	return description;
    }

    public void setDescription(String description) {
	this.description = description;
    }

    public static WorldModel fromWorld(String name, World world) {
	return fromWorld(name, world, null);
    }

    public static WorldModel fromWorld(String name, World world, SimulationFile simulation) {
	WorldModel result = new WorldModel();
	StringBuilder description = new StringBuilder("Available Locations: ");

	for (Location location : world.getLocations()) {
	    description.append(location.getFullPath());
	    if (location.getState() != null && !location.getState().isBlank()) {
		description.append(" (").append(location.getState()).append(")");
	    }
	    description.append("; ");
	}

	for (Agent agent : world.getAgents()) {
	    if (agent.getFullName().equals(name)) {
		continue;
	    }
	    description.append(agent.getFullName())
		.append(" is ")
		.append(agent.getCurrentActivity())
		.append(" at ")
		.append(agent.getLocation() == null ? "Unknown" : agent.getLocation().getFullPath())
		.append(". ");
	}

	if (simulation != null && simulation.getWorldBuilding() != null) {
	    description.append("World summary: ").append(simulation.getWorldBuilding().getSummary()).append(". ");
	    if (simulation.getWorldBuilding().getRules() != null && !simulation.getWorldBuilding().getRules().isEmpty()) {
		description.append("World-building rules: ")
		    .append(String.join("; ", simulation.getWorldBuilding().getRules()))
		    .append(". ");
	    }
	}

	if (simulation != null && simulation.getDailyRhythm() != null) {
	    SimulationFile.DailyRhythmSeed rhythm = simulation.getDailyRhythm();
	    description.append("Daily rhythm: breakfast ")
		.append(rhythm.getBreakfast())
		.append(", lunch ")
		.append(rhythm.getLunch())
		.append(", dinner ")
		.append(rhythm.getDinner())
		.append(", morning tea ")
		.append(rhythm.getMorningTea())
		.append(", afternoon tea ")
		.append(rhythm.getAfternoonTea())
		.append(", snack ")
		.append(rhythm.getSnack())
		.append(". ");
	}

	result.setDescription(description.toString().trim());

	return result;
    }
}

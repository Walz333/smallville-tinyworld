package io.github.nickm980.smallville.ueia.fixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

public final class CanonicalFixtures {

    private static final List<FixtureDefinition> DEFINITIONS = List.of(
	new FixtureDefinition(FixtureRole.ARCHIVE_RECORD, "docs/examples/promoted-archive-record-v1.md"),
	new FixtureDefinition(FixtureRole.DERIVED_PACKET, "docs/examples/promoted-derived-packet-v1.md"),
	new FixtureDefinition(FixtureRole.AGENT_BRIEF, "docs/examples/promoted-agent-brief-v1.md"),
	new FixtureDefinition(FixtureRole.REVIEW_DECISION, "docs/examples/promoted-review-decision-v1.md")
    );

    private CanonicalFixtures() {
    }

    public static List<FixtureRef> all() {
	return DEFINITIONS.stream().map(CanonicalFixtures::resolve).toList();
    }

    public static FixtureRef require(FixtureRole role) {
	Objects.requireNonNull(role, "role");
	return DEFINITIONS
	    .stream()
	    .filter(definition -> definition.role() == role)
	    .findFirst()
	    .map(CanonicalFixtures::resolve)
	    .orElseThrow(() -> new IllegalStateException("Unknown canonical fixture role: " + role));
    }

    private static FixtureRef resolve(FixtureDefinition definition) {
	Path resolvedPath = resolveCanonicalPath(definition.relativePath());
	return new FixtureRef(definition.role(), definition.relativePath(), resolvedPath);
    }

    private static Path resolveCanonicalPath(String relativePath) {
	Path[] candidates = new Path[] {
	    Paths.get(relativePath),
	    Paths.get("..").resolve(relativePath)
	};

	for (Path candidate : candidates) {
	    Path normalized = candidate.normalize().toAbsolutePath();
	    if (Files.exists(normalized)) {
		return normalized;
	    }
	}

	throw new IllegalStateException("Could not resolve canonical fixture path: " + relativePath);
    }

    public enum FixtureRole {
	ARCHIVE_RECORD,
	DERIVED_PACKET,
	AGENT_BRIEF,
	REVIEW_DECISION
    }

    public record FixtureRef(FixtureRole role, String relativePath, Path resolvedPath) {
	public FixtureRef {
	    Objects.requireNonNull(role, "role");
	    Objects.requireNonNull(relativePath, "relativePath");
	    Objects.requireNonNull(resolvedPath, "resolvedPath");
	}
    }

    private record FixtureDefinition(FixtureRole role, String relativePath) {
	private FixtureDefinition {
	    Objects.requireNonNull(role, "role");
	    Objects.requireNonNull(relativePath, "relativePath");
	}
    }
}

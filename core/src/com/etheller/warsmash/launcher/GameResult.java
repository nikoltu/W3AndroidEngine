package com.etheller.warsmash.launcher;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.SerializationException;

/** How a game started from the launcher ended, as the launcher takes it back. */
public final class GameResult {
	/** 2: the score of the local player, the time played, the difficulty and the map's name. */
	public static final int FORMAT = 2;
	/** The engine did not tell the campaign (a game that did not get as far, a result of an older format). */
	public static final int CAMPAIGN_UNKNOWN = -2;

	/**
	 * victory, defeat, tie, quit (left the game to the menu), exit (closed the program), error (not started) or crash
	 * (an exception ended the program).
	 */
	public String result;
	public String source;
	/** The campaign the game played was a mission of, by the number scripts know it by. */
	public int campaign = CAMPAIGN_UNKNOWN;
	public String map;
	public String profile;
	public String loadFailure;
	/** The race the player played, a random one as it was drawn; 0 when the engine did not tell. */
	public int race;
	public long finished;
	/** The name the map gives itself in its settings, empty when the engine did not tell. */
	public String mapName = "";
	/** The game time played, in seconds of the game's clock. */
	public int seconds;
	/** easy, normal, hard or insane; empty when the engine did not tell. */
	public String difficulty = "";
	/** The local player's score, as the score screen counts it; null when the game did not get as far. */
	public Score score;

	/** The numbers of the score screen the launcher shows again. */
	public static final class Score {
		public int heroScore;
		public int heroesKilled;
		public int unitScore;
		public int unitsTrained;
		public int unitsKilled;
		public int structuresBuilt;
		public int structuresRazed;
		public int resourceScore;
		public int totalScore;
		public int goldMined;
		public int lumberHarvested;
	}

	public boolean failed() {
		return "error".equals(this.result) || (this.loadFailure != null);
	}

	public boolean crashed() {
		return "crash".equals(this.result);
	}

	/** Whether the player lost: a defeat, or a game left before it was won, as the Reforged result counts it. */
	public boolean lost() {
		return "defeat".equals(this.result) || "quit".equals(this.result);
	}

	/** The time played as the score screen shows it, h:mm:ss. */
	public String playTime() {
		final int total = Math.max(0, this.seconds);
		return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60);
	}

	public String toJson() {
		final JsonValue json = new JsonValue(JsonValue.ValueType.object);
		json.addChild("version", new JsonValue(FORMAT));
		json.addChild("result", text(this.result));
		json.addChild("source", text(this.source));
		if (this.campaign != CAMPAIGN_UNKNOWN) {
			json.addChild("campaign", new JsonValue(this.campaign));
		}
		json.addChild("map", text(this.map));
		json.addChild("profile", text(this.profile));
		if (this.loadFailure != null) {
			json.addChild("loadFailure", new JsonValue(this.loadFailure));
		}
		if (this.race > 0) {
			json.addChild("race", new JsonValue(this.race));
		}
		json.addChild("finished", new JsonValue(this.finished));
		json.addChild("mapName", text(this.mapName));
		json.addChild("seconds", new JsonValue(this.seconds));
		json.addChild("difficulty", text(this.difficulty));
		final Score score = this.score;
		if (score != null) {
			final JsonValue numbers = new JsonValue(JsonValue.ValueType.object);
			numbers.addChild("heroScore", new JsonValue(score.heroScore));
			numbers.addChild("heroesKilled", new JsonValue(score.heroesKilled));
			numbers.addChild("unitScore", new JsonValue(score.unitScore));
			numbers.addChild("unitsTrained", new JsonValue(score.unitsTrained));
			numbers.addChild("unitsKilled", new JsonValue(score.unitsKilled));
			numbers.addChild("structuresBuilt", new JsonValue(score.structuresBuilt));
			numbers.addChild("structuresRazed", new JsonValue(score.structuresRazed));
			numbers.addChild("resourceScore", new JsonValue(score.resourceScore));
			numbers.addChild("totalScore", new JsonValue(score.totalScore));
			numbers.addChild("goldMined", new JsonValue(score.goldMined));
			numbers.addChild("lumberHarvested", new JsonValue(score.lumberHarvested));
			json.addChild("score", numbers);
		}
		return json.prettyPrint(JsonWriter.OutputType.json, 0);
	}

	/**
	 * A result as {@link #toJson()} wrote it; a result of an older format reads with the fields it lacks left empty.
	 *
	 * @throws IllegalArgumentException when the text is not a result
	 */
	public static GameResult fromJson(final String text) {
		final JsonValue json;
		try {
			json = new JsonReader().parse(text);
		}
		catch (final SerializationException e) {
			throw new IllegalArgumentException("Not a game result", e);
		}
		if ((json == null) || !json.isObject()) {
			throw new IllegalArgumentException("Not a game result");
		}
		final GameResult result = new GameResult();
		result.result = json.getString("result", "quit");
		result.source = json.getString("source", "");
		result.campaign = json.getInt("campaign", CAMPAIGN_UNKNOWN);
		result.map = json.getString("map", "");
		result.profile = json.getString("profile", "");
		result.loadFailure = json.has("loadFailure") ? json.getString("loadFailure", null) : null;
		result.race = json.getInt("race", 0);
		result.finished = json.getLong("finished", 0);
		result.mapName = json.getString("mapName", "");
		result.seconds = json.getInt("seconds", 0);
		result.difficulty = json.getString("difficulty", "");
		final JsonValue numbers = json.get("score");
		if ((numbers != null) && numbers.isObject()) {
			final Score score = new Score();
			score.heroScore = numbers.getInt("heroScore", 0);
			score.heroesKilled = numbers.getInt("heroesKilled", 0);
			score.unitScore = numbers.getInt("unitScore", 0);
			score.unitsTrained = numbers.getInt("unitsTrained", 0);
			score.unitsKilled = numbers.getInt("unitsKilled", 0);
			score.structuresBuilt = numbers.getInt("structuresBuilt", 0);
			score.structuresRazed = numbers.getInt("structuresRazed", 0);
			score.resourceScore = numbers.getInt("resourceScore", 0);
			score.totalScore = numbers.getInt("totalScore", 0);
			score.goldMined = numbers.getInt("goldMined", 0);
			score.lumberHarvested = numbers.getInt("lumberHarvested", 0);
			result.score = score;
		}
		return result;
	}

	private static JsonValue text(final String value) {
		return new JsonValue(value == null ? "" : value);
	}
}

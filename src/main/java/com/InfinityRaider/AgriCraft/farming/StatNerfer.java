package com.InfinityRaider.AgriCraft.farming;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToIntFunction;

import com.InfinityRaider.AgriCraft.handler.ConfigurationHandler;
import com.InfinityRaider.AgriCraft.utility.LogHelper;

/**
 * PIWCS hack to nerf AgriCraft.
 * @author OLEGSHA
 */
public class StatNerfer {
	
	/**
	 * Enum that holds getters and setters for all three crop stats.
	 */
	private static enum Field {
		
		GAIN     (PlantStats::getGain,     PlantStats::setGain),
		GROWTH   (PlantStats::getGrowth,   PlantStats::setGrowth),
		STRENGTH (PlantStats::getStrength, PlantStats::setStrength);
		
		@FunctionalInterface
		private interface Setter {
			void set(PlantStats stats, int value);
		}
		
		private final ToIntFunction<PlantStats> getter;
		private final Setter setter;
		
		private Field(ToIntFunction<PlantStats> getter, Setter setter) {
			this.getter = getter;
			this.setter = setter;
		}
		
		public int get(PlantStats stats) {
			return getter.applyAsInt(stats);
		}
		
		public void set(PlantStats stats, int value) {
			setter.set(stats, value);
		}
		
	}
	
	/**
	 * Random used to decide which stat to nerf.
	 */
	private static final Random RANDOM = new Random();
	
	/**
	 * True iff an invalid maximum score has been detected and reported at least once.
	 */
	private static final AtomicBoolean HAS_REPORTED_INVALID_MAX_SCORE = new AtomicBoolean(false);
	
	/**
	 * Keeps track of the stats that can still be nerfed while nerfing.
	 */
	private static class NerfableFieldsTracker {
		
		/**
		 * Amount of nerfable fields, i.e. the number of indices {@code i} of array {@code canNerf} such that {@code canNerf[i] == true}. 
		 */
		int size = Field.values().length;
		
		boolean[] canNerf = new boolean[size];
		
		{
			Arrays.fill(canNerf, true);
		}

		/**
		 * Removes stats that are no longer nerfable from this tracker.
		 * This code expects that non-nerfable stats cannot become nerfable.
		 * @param stats the stats to update according to
		 */
		public void update(PlantStats stats) {
			for (int i = 0; i < canNerf.length; ++i) {
				
				if (!canNerf[i]) continue;
				
				if (Field.values()[i].get(stats) <= 1) {
					size--;
					canNerf[i] = false;
				}
				
			}
		}
		
		/**
		 * Returns a randomly chosen nerfable {@link Field Stat}.
		 * @throws IllegalStateException if no stats are nerfable.
		 * @return a {@link Field Stat} nerfable according to previous invocations of {@link #update(PlantStats)}.
		 */
		public Field getRandomNerfableField() {
			int skip;
			
			try {
				skip = RANDOM.nextInt(size);
			} catch (IllegalArgumentException e) {
				throw new IllegalStateException("No stats are nerfable", e);
			}
			
			int i;
			for (i = 0; skip > 0; ++i) {
				if (!canNerf[i]) continue;
				skip--;
			}
			
			return Field.values()[i];
		}
		
		public boolean isEmpty() {
			return size == 0;
		}
		
	}
	
	/**
	 * Nerfs provided {@link PlantStats} object if necessary.
	 * @param stats the stats to nerf
	 */
	public static void nerfStats(PlantStats stats) {
		int maxScore = getMaxScore();
		
		if (score(stats) <= maxScore) return;
		
		NerfableFieldsTracker nerfableStats = new NerfableFieldsTracker();
		
		do {
			
			nerfableStats.update(stats);
			
			if (nerfableStats.isEmpty()) {
				// Whoops.
				reportInvalidMaxStatEncounter(stats);
				return;
			}
			
			doNerf(stats, nerfableStats);
			
		} while (score(stats) > maxScore); // Wow, my first proper use of "do {} while ();"! 
	}

	/**
	 * Nerfs a single nerfable stat by 1.
	 * @param stats the {@link PlantStats} object to affect
	 * @param nerfableStats cache of nerfable stats
	 * @throws IllegalStateException if no stats are nerfable.
	 */
	private static void doNerf(PlantStats stats, NerfableFieldsTracker nerfableStats) {
		Field field = nerfableStats.getRandomNerfableField();
		field.set(stats, field.get(stats) - 1);
	}
	
	/**
	 * Scores the given {@link PlantStats} object.
	 * @param stats the stats to score.
	 * @return the stats' score.
	 */
	private static int score(PlantStats stats) {
		int sum = 0;
		
		for (Field field : Field.values()) {
			sum += sq(field.get(stats));
		}
		
		return sum;
	}
	
	private static int getMaxScore() {
		return ConfigurationHandler.maxStatScore;
	}
	
	/**
	 * Computers the square of the argument.
	 * @param x the number to square
	 * @return {@code x * x}.
	 */
	private static int sq(int x) {
		return x * x;
	}
	
	private static void reportInvalidMaxStatEncounter(PlantStats stats) {
		if (!HAS_REPORTED_INVALID_MAX_SCORE.getAndSet(true)) {
			LogHelper.fatal(
					"Crop stats {gain: " + stats.getGain() +
					"; growth: " + stats.getGrowth() +
					"; strength: " + stats.getStrength() +
					"} have stat score " + score(stats) +
					" that cannot be reduced into bounds [0; " + getMaxScore() + "]."
			);
		}
	}
	
}

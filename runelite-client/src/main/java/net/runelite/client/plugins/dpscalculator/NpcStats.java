/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.dpscalculator;

import lombok.Builder;
import lombok.Data;

/**
 * Represents the combat stats of an NPC for DPS calculations.
 */
@Data
@Builder(toBuilder = true)
public class NpcStats
{
	/**
	 * NPC ID
	 */
	private int id;

	/**
	 * NPC name
	 */
	private String name;

	/**
	 * Combat level
	 */
	private int combatLevel;

	/**
	 * Hitpoints
	 */
	private int hitpoints;

	/**
	 * Defence level
	 */
	private int defenceLevel;

	/**
	 * Magic level (used for magic defence calculation)
	 */
	private int magicLevel;

	/**
	 * Defence bonus vs stab attacks
	 */
	private int defenceStab;

	/**
	 * Defence bonus vs slash attacks
	 */
	private int defenceSlash;

	/**
	 * Defence bonus vs crush attacks
	 */
	private int defenceCrush;

	/**
	 * Defence bonus vs magic attacks
	 */
	private int defenceMagic;

	/**
	 * Defence bonus vs ranged attacks
	 */
	private int defenceRanged;

	/**
	 * Size of the NPC (1x1, 2x2, 3x3, etc.)
	 */
	@Builder.Default
	private int size = 1;

	/**
	 * NPC attributes (demon, dragon, undead, etc.)
	 */
	@Builder.Default
	private NpcAttributes attributes = NpcAttributes.builder().build();

	/**
	 * Creates a default NPC stats object for unknown NPCs
	 */
	public static NpcStats createDefault(int id, String name, int combatLevel)
	{
		// Estimate stats based on combat level
		int estimatedDef = Math.max(1, combatLevel / 2);
		int estimatedHp = Math.max(10, combatLevel * 2);

		return NpcStats.builder()
			.id(id)
			.name(name)
			.combatLevel(combatLevel)
			.hitpoints(estimatedHp)
			.defenceLevel(estimatedDef)
			.magicLevel(estimatedDef)
			.defenceStab(0)
			.defenceSlash(0)
			.defenceCrush(0)
			.defenceMagic(0)
			.defenceRanged(0)
			.build();
	}

	@Data
	@Builder
	public static class NpcAttributes
	{
		@Builder.Default
		private boolean demon = false;
		@Builder.Default
		private boolean dragon = false;
		@Builder.Default
		private boolean undead = false;
		@Builder.Default
		private boolean kalphite = false;
		@Builder.Default
		private boolean leafy = false;
		@Builder.Default
		private boolean vampyre = false;
		@Builder.Default
		private boolean xerician = false;
		@Builder.Default
		private boolean fiery = false;
		@Builder.Default
		private boolean shade = false;
		@Builder.Default
		private boolean golem = false;
	}
}

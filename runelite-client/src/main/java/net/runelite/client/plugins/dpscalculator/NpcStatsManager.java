/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.dpscalculator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.NPCManager;

/**
 * Manages NPC combat stats database for DPS calculations.
 * Data sourced from Bitterkoekje's DPS calculator spreadsheet.
 */
@Slf4j
@Singleton
public class NpcStatsManager
{
	private final NPCManager npcManager;
	private final Gson gson;

	// Map of NPC name (lowercase) -> NpcStats
	private final Map<String, NpcStats> npcStatsByName = new HashMap<>();

	@Inject
	public NpcStatsManager(NPCManager npcManager, Gson gson)
	{
		this.npcManager = npcManager;
		this.gson = gson;
		loadNpcStats();
	}

	/**
	 * Load NPC stats from JSON resource file.
	 */
	private void loadNpcStats()
	{
		try (InputStream is = getClass().getResourceAsStream("npc_stats.json"))
		{
			if (is == null)
			{
				log.warn("Could not find npc_stats.json resource");
				return;
			}

			InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
			Type type = new TypeToken<NpcStatsFile>() {}.getType();
			NpcStatsFile file = gson.fromJson(reader, type);

			if (file != null && file.getNpcs() != null)
			{
				for (NpcStatsEntry entry : file.getNpcs())
				{
					NpcStats stats = entryToStats(entry);
					npcStatsByName.put(entry.getName().toLowerCase(), stats);
				}
			}

			log.info("Loaded {} NPC stats from database", npcStatsByName.size());
		}
		catch (Exception e)
		{
			log.error("Failed to load NPC stats from JSON", e);
		}
	}

	private NpcStats entryToStats(NpcStatsEntry entry)
	{
		// Build attributes from the list
		boolean isDemon = false, isDragon = false, isUndead = false;
		boolean isKalphite = false, isLeafy = false, isVampyre = false;
		boolean isXerician = false, isFiery = false, isShade = false, isGolem = false;

		if (entry.getAttributes() != null)
		{
			for (String attr : entry.getAttributes())
			{
				switch (attr.toLowerCase())
				{
					case "demon":
						isDemon = true;
						break;
					case "dragon":
						isDragon = true;
						break;
					case "undead":
						isUndead = true;
						break;
					case "kalphite":
						isKalphite = true;
						break;
					case "leafy":
						isLeafy = true;
						break;
					case "vampyre":
						isVampyre = true;
						break;
					case "xerician":
						isXerician = true;
						break;
					case "fiery":
						isFiery = true;
						break;
					case "shade":
						isShade = true;
						break;
					case "golem":
						isGolem = true;
						break;
				}
			}
		}

		NpcStats.NpcAttributes attributes = NpcStats.NpcAttributes.builder()
			.demon(isDemon)
			.dragon(isDragon)
			.undead(isUndead)
			.kalphite(isKalphite)
			.leafy(isLeafy)
			.vampyre(isVampyre)
			.xerician(isXerician)
			.fiery(isFiery)
			.shade(isShade)
			.golem(isGolem)
			.build();

		return NpcStats.builder()
			.id(0) // ID not stored in JSON, matched by name
			.name(entry.getName())
			.combatLevel(entry.getCombatLevel())
			.hitpoints(entry.getHitpoints())
			.defenceLevel(entry.getDefenceLevel())
			.magicLevel(entry.getMagicLevel())
			.defenceStab(entry.getDefenceStab())
			.defenceSlash(entry.getDefenceSlash())
			.defenceCrush(entry.getDefenceCrush())
			.defenceMagic(entry.getDefenceMagic())
			.defenceRanged(entry.getDefenceRanged())
			.attributes(attributes)
			.build();
	}

	/**
	 * Get NPC stats by ID and name, falling back to estimated stats if not found.
	 */
	public NpcStats getNpcStats(int npcId, String npcName)
	{
		// Try exact name match first
		if (npcName != null)
		{
			NpcStats stats = npcStatsByName.get(npcName.toLowerCase());
			if (stats != null)
			{
				return stats.toBuilder().id(npcId).build();
			}

			// Try partial match (e.g., "Vorkath" matches "Vorkath (quest)")
			for (Map.Entry<String, NpcStats> entry : npcStatsByName.entrySet())
			{
				if (entry.getKey().startsWith(npcName.toLowerCase()) ||
					npcName.toLowerCase().startsWith(entry.getKey()))
				{
					return entry.getValue().toBuilder().id(npcId).build();
				}
			}
		}

		// Get HP from RuneLite's NPCManager if available
		Integer hp = npcManager.getHealth(npcId);

		// Create estimated stats
		NpcStats defaultStats = NpcStats.createDefault(npcId, npcName != null ? npcName : "Unknown", 0);
		if (hp != null)
		{
			defaultStats.setHitpoints(hp);
		}

		log.debug("No stats found for NPC {} ({}), using estimates", npcName, npcId);
		return defaultStats;
	}

	/**
	 * Check if we have accurate stats for this NPC
	 */
	public boolean hasAccurateStats(int npcId)
	{
		// We don't have IDs in the JSON, but the caller passes the name
		// This method is called after getNpcStats which already matches
		return false; // Will be set correctly via the panel
	}

	/**
	 * Check if we have accurate stats for this NPC by name
	 */
	public boolean hasAccurateStats(String npcName)
	{
		if (npcName == null)
		{
			return false;
		}

		// Check exact match
		if (npcStatsByName.containsKey(npcName.toLowerCase()))
		{
			return true;
		}

		// Check partial match
		for (String key : npcStatsByName.keySet())
		{
			if (key.startsWith(npcName.toLowerCase()) ||
				npcName.toLowerCase().startsWith(key))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Get all NPC names in the database (for autocomplete)
	 */
	public List<String> getAllNpcNames()
	{
		return new ArrayList<>(npcStatsByName.keySet());
	}

	/**
	 * JSON file structure
	 */
	@Data
	private static class NpcStatsFile
	{
		private List<NpcStatsEntry> npcs;
	}

	/**
	 * JSON entry structure
	 */
	@Data
	private static class NpcStatsEntry
	{
		private String name;
		private int combatLevel;
		private int hitpoints;
		private int defenceLevel;
		private int magicLevel;
		private int defenceStab;
		private int defenceSlash;
		private int defenceCrush;
		private int defenceMagic;
		private int defenceRanged;
		private String elementalWeakness;
		private List<String> attributes;
	}
}

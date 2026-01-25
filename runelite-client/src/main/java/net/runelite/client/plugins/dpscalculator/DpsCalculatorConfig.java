/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.dpscalculator;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("dpscalculator")
public interface DpsCalculatorConfig extends Config
{
	@ConfigSection(
		name = "Potion Boosts",
		description = "Configure potion boost settings",
		position = 0
	)
	String potionSection = "potionSection";

	@ConfigSection(
		name = "Prayer",
		description = "Configure prayer settings",
		position = 1
	)
	String prayerSection = "prayerSection";

	@ConfigItem(
		keyName = "potionBoost",
		name = "Potion Boost",
		description = "Select the potion boost to use in calculations",
		section = potionSection,
		position = 0
	)
	default PotionBoost potionBoost()
	{
		return PotionBoost.NONE;
	}

	@ConfigItem(
		keyName = "meleePrayer",
		name = "Melee Prayer",
		description = "Select melee prayer for calculations",
		section = prayerSection,
		position = 0
	)
	default MeleePrayer meleePrayer()
	{
		return MeleePrayer.NONE;
	}

	@ConfigItem(
		keyName = "rangedPrayer",
		name = "Ranged Prayer",
		description = "Select ranged prayer for calculations",
		section = prayerSection,
		position = 1
	)
	default RangedPrayer rangedPrayer()
	{
		return RangedPrayer.NONE;
	}

	@ConfigItem(
		keyName = "magicPrayer",
		name = "Magic Prayer",
		description = "Select magic prayer for calculations",
		section = prayerSection,
		position = 2
	)
	default MagicPrayer magicPrayer()
	{
		return MagicPrayer.NONE;
	}

	@ConfigItem(
		keyName = "onSlayerTask",
		name = "On Slayer Task",
		description = "Whether to apply slayer helm/black mask bonus",
		position = 3
	)
	default boolean onSlayerTask()
	{
		return false;
	}

	enum PotionBoost
	{
		NONE("None", 0, 0),
		SUPER_ATTACK("Super Attack", 5, 15),
		SUPER_STRENGTH("Super Strength", 5, 15),
		SUPER_COMBAT("Super Combat", 5, 15),
		OVERLOAD("Overload (CoX)", 6, 16),
		DIVINE_SUPER_COMBAT("Divine Super Combat", 5, 15),
		RANGING_POTION("Ranging Potion", 4, 10),
		SUPER_RANGING("Super Ranging", 5, 15),
		DIVINE_RANGING("Divine Ranging", 5, 15),
		IMBUED_HEART("Imbued Heart", 1, 10),
		SATURATED_HEART("Saturated Heart", 4, 10);

		private final String name;
		private final int flatBoost;
		private final int percentBoost;

		PotionBoost(String name, int flatBoost, int percentBoost)
		{
			this.name = name;
			this.flatBoost = flatBoost;
			this.percentBoost = percentBoost;
		}

		public int getFlatBoost()
		{
			return flatBoost;
		}

		public int getPercentBoost()
		{
			return percentBoost;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum MeleePrayer
	{
		NONE("None", 1.0, 1.0),
		BURST_OF_STRENGTH("Burst of Strength", 1.0, 1.05),
		SUPERHUMAN_STRENGTH("Superhuman Strength", 1.0, 1.10),
		ULTIMATE_STRENGTH("Ultimate Strength", 1.0, 1.15),
		CHIVALRY("Chivalry", 1.15, 1.18),
		PIETY("Piety", 1.20, 1.23);

		private final String name;
		private final double attackMultiplier;
		private final double strengthMultiplier;

		MeleePrayer(String name, double attackMultiplier, double strengthMultiplier)
		{
			this.name = name;
			this.attackMultiplier = attackMultiplier;
			this.strengthMultiplier = strengthMultiplier;
		}

		public double getAttackMultiplier()
		{
			return attackMultiplier;
		}

		public double getStrengthMultiplier()
		{
			return strengthMultiplier;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum RangedPrayer
	{
		NONE("None", 1.0, 1.0),
		SHARP_EYE("Sharp Eye", 1.05, 1.05),
		HAWK_EYE("Hawk Eye", 1.10, 1.10),
		EAGLE_EYE("Eagle Eye", 1.15, 1.15),
		RIGOUR("Rigour", 1.20, 1.23);

		private final String name;
		private final double accuracyMultiplier;
		private final double damageMultiplier;

		RangedPrayer(String name, double accuracyMultiplier, double damageMultiplier)
		{
			this.name = name;
			this.accuracyMultiplier = accuracyMultiplier;
			this.damageMultiplier = damageMultiplier;
		}

		public double getAccuracyMultiplier()
		{
			return accuracyMultiplier;
		}

		public double getDamageMultiplier()
		{
			return damageMultiplier;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum MagicPrayer
	{
		NONE("None", 1.0),
		MYSTIC_WILL("Mystic Will", 1.05),
		MYSTIC_LORE("Mystic Lore", 1.10),
		MYSTIC_MIGHT("Mystic Might", 1.15),
		AUGURY("Augury", 1.25);

		private final String name;
		private final double accuracyMultiplier;

		MagicPrayer(String name, double accuracyMultiplier)
		{
			this.name = name;
			this.accuracyMultiplier = accuracyMultiplier;
		}

		public double getAccuracyMultiplier()
		{
			return accuracyMultiplier;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

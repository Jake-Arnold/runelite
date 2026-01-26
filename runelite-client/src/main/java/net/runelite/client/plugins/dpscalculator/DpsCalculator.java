/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.dpscalculator;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * DPS Calculator using standard OSRS formulas.
 * Based on Bitterkoekje's DPS calculator.
 *
 * DPS = (Hit Chance * Max Hit) / (Attack Speed * 0.6)
 * Hit Chance = Attack Roll / (Attack Roll + Defence Roll + 1)
 */
@Slf4j
public class DpsCalculator
{
	/**
	 * Calculate DPS for melee combat
	 */
	public static DpsResult calculateMeleeDps(
		int attackLevel,
		int strengthLevel,
		int attackBonus,
		int strengthBonus,
		int attackSpeed,
		CombatStyle style,
		NpcStats npc,
		DpsCalculatorConfig.MeleePrayer prayer,
		DpsCalculatorConfig.PotionBoost potion,
		boolean onSlayerTask,
		boolean voidSet,
		double specialMultiplier)
	{
		// Apply potion boost
		int boostedAttack = applyPotionBoost(attackLevel, potion);
		int boostedStrength = applyPotionBoost(strengthLevel, potion);

		// Apply prayer multiplier
		double prayerAttackMult = prayer.getAttackMultiplier();
		double prayerStrengthMult = prayer.getStrengthMultiplier();

		// Effective levels
		int effectiveAttack = (int) ((boostedAttack * prayerAttackMult) + style.getAttackBonus() + 8);
		int effectiveStrength = (int) ((boostedStrength * prayerStrengthMult) + style.getStrengthBonus() + 8);

		// Void melee bonus
		if (voidSet)
		{
			effectiveAttack = (int) (effectiveAttack * 1.10);
			effectiveStrength = (int) (effectiveStrength * 1.10);
		}

		// Slayer helm/black mask bonus
		double slayerMult = onSlayerTask ? 7.0 / 6.0 : 1.0; // 16.67% bonus

		// Max attack roll
		int maxAttackRoll = (int) (effectiveAttack * (attackBonus + 64) * slayerMult * specialMultiplier);

		// Max hit calculation: floor(0.5 + EffectiveStrength * (EquipmentStrength + 64) / 640)
		int maxHit = (int) Math.floor(0.5 + (effectiveStrength * (strengthBonus + 64) / 640.0));
		maxHit = (int) (maxHit * slayerMult * specialMultiplier);

		// NPC defence roll
		int npcDefenceBonus = getDefenceBonusForStyle(npc, style);
		int maxDefenceRoll = (npc.getDefenceLevel() + 9) * (npcDefenceBonus + 64);

		// Accuracy calculation
		double accuracy;
		if (maxAttackRoll > maxDefenceRoll)
		{
			accuracy = 1.0 - ((double) (maxDefenceRoll + 2) / (2 * (maxAttackRoll + 1)));
		}
		else
		{
			accuracy = (double) maxAttackRoll / (2 * (maxDefenceRoll + 1));
		}

		// DPS calculation
		double attackInterval = attackSpeed * 0.6; // seconds per attack
		double dps = (accuracy * maxHit) / (2.0 * attackInterval);

		// Time to kill
		double expectedHitsToKill = npc.getHitpoints() / (accuracy * maxHit / 2.0);
		double timeToKill = expectedHitsToKill * attackInterval;

		return DpsResult.builder()
			.dps(dps)
			.maxHit(maxHit)
			.accuracy(accuracy * 100)
			.maxAttackRoll(maxAttackRoll)
			.maxDefenceRoll(maxDefenceRoll)
			.attackSpeed(attackSpeed)
			.timeToKill(timeToKill)
			.combatStyle(style.getName())
			.build();
	}

	/**
	 * Calculate DPS for melee combat with explicit attack type and style bonuses
	 */
	public static DpsResult calculateMeleeDps(
		int attackLevel,
		int strengthLevel,
		int attackBonus,
		int strengthBonus,
		int attackSpeed,
		AttackType attackType,
		int styleAttackBonus,
		int styleStrengthBonus,
		NpcStats npc,
		DpsCalculatorConfig.MeleePrayer prayer,
		DpsCalculatorConfig.PotionBoost potion,
		boolean onSlayerTask,
		boolean voidSet,
		double specialMultiplier)
	{
		int boostedAttack = applyPotionBoost(attackLevel, potion);
		int boostedStrength = applyPotionBoost(strengthLevel, potion);

		double prayerAttackMult = prayer.getAttackMultiplier();
		double prayerStrengthMult = prayer.getStrengthMultiplier();

		int effectiveAttack = (int) ((boostedAttack * prayerAttackMult) + styleAttackBonus + 8);
		int effectiveStrength = (int) ((boostedStrength * prayerStrengthMult) + styleStrengthBonus + 8);

		if (voidSet)
		{
			effectiveAttack = (int) (effectiveAttack * 1.10);
			effectiveStrength = (int) (effectiveStrength * 1.10);
		}

		double slayerMult = onSlayerTask ? 7.0 / 6.0 : 1.0;

		int maxAttackRoll = (int) (effectiveAttack * (attackBonus + 64) * slayerMult * specialMultiplier);

		int maxHit = (int) Math.floor(0.5 + (effectiveStrength * (strengthBonus + 64) / 640.0));
		maxHit = (int) (maxHit * slayerMult * specialMultiplier);

		int npcDefenceBonus = getDefenceBonusForAttackType(npc, attackType);
		int maxDefenceRoll = (npc.getDefenceLevel() + 9) * (npcDefenceBonus + 64);

		double accuracy;
		if (maxAttackRoll > maxDefenceRoll)
		{
			accuracy = 1.0 - ((double) (maxDefenceRoll + 2) / (2 * (maxAttackRoll + 1)));
		}
		else
		{
			accuracy = (double) maxAttackRoll / (2 * (maxDefenceRoll + 1));
		}

		double attackInterval = attackSpeed * 0.6;
		double dps = (accuracy * maxHit) / (2.0 * attackInterval);

		double expectedHitsToKill = npc.getHitpoints() / (accuracy * maxHit / 2.0);
		double timeToKill = expectedHitsToKill * attackInterval;

		return DpsResult.builder()
			.dps(dps)
			.maxHit(maxHit)
			.accuracy(accuracy * 100)
			.maxAttackRoll(maxAttackRoll)
			.maxDefenceRoll(maxDefenceRoll)
			.attackSpeed(attackSpeed)
			.timeToKill(timeToKill)
			.combatStyle(attackType != null ? attackType.getName() : "Melee")
			.build();
	}

	/**
	 * Calculate DPS for ranged combat
	 */
	public static DpsResult calculateRangedDps(
		int rangedLevel,
		int rangedAttackBonus,
		int rangedStrengthBonus,
		int attackSpeed,
		NpcStats npc,
		DpsCalculatorConfig.RangedPrayer prayer,
		DpsCalculatorConfig.PotionBoost potion,
		boolean onSlayerTask,
		boolean voidSet,
		double specialMultiplier)
	{
		// Apply potion boost
		int boostedRanged = applyPotionBoost(rangedLevel, potion);

		// Apply prayer multiplier
		double prayerAccuracyMult = prayer.getAccuracyMultiplier();
		double prayerDamageMult = prayer.getDamageMultiplier();

		// Effective level (accurate style assumed: +3 attack)
		int effectiveRangedAccuracy = (int) ((boostedRanged * prayerAccuracyMult) + 8 + 3);
		int effectiveRangedStrength = (int) ((boostedRanged * prayerDamageMult) + 8);

		// Void ranged bonus
		if (voidSet)
		{
			effectiveRangedAccuracy = (int) (effectiveRangedAccuracy * 1.10);
			effectiveRangedStrength = (int) (effectiveRangedStrength * 1.125); // Elite void
		}

		// Slayer helm bonus
		double slayerMult = onSlayerTask ? 1.15 : 1.0;

		// Max attack roll
		int maxAttackRoll = (int) (effectiveRangedAccuracy * (rangedAttackBonus + 64) * slayerMult * specialMultiplier);

		// Max hit
		int maxHit = (int) Math.floor(0.5 + (effectiveRangedStrength * (rangedStrengthBonus + 64) / 640.0));
		maxHit = (int) (maxHit * slayerMult * specialMultiplier);

		// NPC defence roll (ranged)
		int npcDefenceBonus = npc.getDefenceRanged();
		int maxDefenceRoll = (npc.getDefenceLevel() + 9) * (npcDefenceBonus + 64);

		// Accuracy
		double accuracy;
		if (maxAttackRoll > maxDefenceRoll)
		{
			accuracy = 1.0 - ((double) (maxDefenceRoll + 2) / (2 * (maxAttackRoll + 1)));
		}
		else
		{
			accuracy = (double) maxAttackRoll / (2 * (maxDefenceRoll + 1));
		}

		// DPS
		double attackInterval = attackSpeed * 0.6;
		double dps = (accuracy * maxHit) / (2.0 * attackInterval);

		// Time to kill
		double expectedHitsToKill = npc.getHitpoints() / (accuracy * maxHit / 2.0);
		double timeToKill = expectedHitsToKill * attackInterval;

		return DpsResult.builder()
			.dps(dps)
			.maxHit(maxHit)
			.accuracy(accuracy * 100)
			.maxAttackRoll(maxAttackRoll)
			.maxDefenceRoll(maxDefenceRoll)
			.attackSpeed(attackSpeed)
			.timeToKill(timeToKill)
			.combatStyle("Ranged")
			.build();
	}

	/**
	 * Calculate DPS for magic combat
	 */
	public static DpsResult calculateMagicDps(
		int magicLevel,
		int magicAttackBonus,
		int baseMagicDamage,
		double magicDamageBonus,
		int attackSpeed,
		NpcStats npc,
		DpsCalculatorConfig.MagicPrayer prayer,
		DpsCalculatorConfig.PotionBoost potion,
		boolean onSlayerTask,
		boolean voidSet)
	{
		// Apply potion boost
		int boostedMagic = applyPotionBoost(magicLevel, potion);

		// Apply prayer multiplier
		double prayerAccuracyMult = prayer.getAccuracyMultiplier();

		// Effective magic level (accurate style: +3)
		int effectiveMagic = (int) ((boostedMagic * prayerAccuracyMult) + 8 + 3);

		// Void mage bonus
		if (voidSet)
		{
			effectiveMagic = (int) (effectiveMagic * 1.45); // Elite void mage
		}

		// Max attack roll
		int maxAttackRoll = effectiveMagic * (magicAttackBonus + 64);

		// Max hit with damage bonus
		int maxHit = (int) (baseMagicDamage * (1.0 + magicDamageBonus / 100.0));

		// Slayer helm bonus (magic)
		if (onSlayerTask)
		{
			maxHit = (int) (maxHit * 1.15);
			maxAttackRoll = (int) (maxAttackRoll * 1.15);
		}

		// NPC magic defence roll (uses 70% magic level + 30% defence level in most cases)
		int npcMagicDefence = npc.getDefenceMagic();
		int effectiveNpcMagic = (int) (npc.getMagicLevel() * 0.7 + npc.getDefenceLevel() * 0.3);
		int maxDefenceRoll = (effectiveNpcMagic + 9) * (npcMagicDefence + 64);

		// Accuracy
		double accuracy;
		if (maxAttackRoll > maxDefenceRoll)
		{
			accuracy = 1.0 - ((double) (maxDefenceRoll + 2) / (2 * (maxAttackRoll + 1)));
		}
		else
		{
			accuracy = (double) maxAttackRoll / (2 * (maxDefenceRoll + 1));
		}

		// DPS
		double attackInterval = attackSpeed * 0.6;
		double dps = (accuracy * maxHit) / (2.0 * attackInterval);

		// Time to kill
		double expectedHitsToKill = npc.getHitpoints() / (accuracy * maxHit / 2.0);
		double timeToKill = expectedHitsToKill * attackInterval;

		return DpsResult.builder()
			.dps(dps)
			.maxHit(maxHit)
			.accuracy(accuracy * 100)
			.maxAttackRoll(maxAttackRoll)
			.maxDefenceRoll(maxDefenceRoll)
			.attackSpeed(attackSpeed)
			.timeToKill(timeToKill)
			.combatStyle("Magic")
			.build();
	}

	private static int applyPotionBoost(int level, DpsCalculatorConfig.PotionBoost potion)
	{
		if (potion == DpsCalculatorConfig.PotionBoost.NONE)
		{
			return level;
		}
		return level + potion.getFlatBoost() + (int) (level * potion.getPercentBoost() / 100.0);
	}

	private static int getDefenceBonusForStyle(NpcStats npc, CombatStyle style)
	{
		switch (style)
		{
			case STAB:
			case LUNGE:
				return npc.getDefenceStab();
			case SLASH:
			case CHOP:
				return npc.getDefenceSlash();
			case CRUSH:
			case SMASH:
			case POUND:
			case PUMMEL:
				return npc.getDefenceCrush();
			default:
				return npc.getDefenceStab();
		}
	}

	private static int getDefenceBonusForAttackType(NpcStats npc, AttackType attackType)
	{
		if (attackType == null)
		{
			return npc.getDefenceStab();
		}

		switch (attackType)
		{
			case SLASH:
				return npc.getDefenceSlash();
			case CRUSH:
				return npc.getDefenceCrush();
			case STAB:
			default:
				return npc.getDefenceStab();
		}
	}

	public enum AttackType
	{
		STAB("Stab"),
		SLASH("Slash"),
		CRUSH("Crush");

		private final String name;

		AttackType(String name)
		{
			this.name = name;
		}

		public String getName()
		{
			return name;
		}
	}

	public enum CombatStyle
	{
		// Stab styles
		STAB("Stab", 0, 0),
		LUNGE("Lunge", 3, 0),

		// Slash styles
		SLASH("Slash", 0, 0),
		CHOP("Chop", 0, 3),

		// Crush styles
		CRUSH("Crush", 0, 0),
		SMASH("Smash", 0, 3),
		POUND("Pound", 0, 0),
		PUMMEL("Pummel", 3, 0),

		// Defensive
		BLOCK("Block", 0, 0),

		// Controlled (gives +1 to all)
		CONTROLLED("Controlled", 1, 1);

		private final String name;
		private final int attackBonus;
		private final int strengthBonus;

		CombatStyle(String name, int attackBonus, int strengthBonus)
		{
			this.name = name;
			this.attackBonus = attackBonus;
			this.strengthBonus = strengthBonus;
		}

		public String getName()
		{
			return name;
		}

		public int getAttackBonus()
		{
			return attackBonus;
		}

		public int getStrengthBonus()
		{
			return strengthBonus;
		}
	}

	@Data
	@Builder
	public static class DpsResult
	{
		private double dps;
		private int maxHit;
		private double accuracy;
		private int maxAttackRoll;
		private int maxDefenceRoll;
		private int attackSpeed;
		private double timeToKill;
		private String combatStyle;

		public String formatTimeToKill()
		{
			int seconds = (int) timeToKill;
			int minutes = seconds / 60;
			seconds = seconds % 60;
			if (minutes > 0)
			{
				return String.format("%dm %ds", minutes, seconds);
			}
			return String.format("%.1fs", timeToKill);
		}
	}
}

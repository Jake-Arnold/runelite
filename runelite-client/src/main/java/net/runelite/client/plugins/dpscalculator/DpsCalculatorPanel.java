/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.dpscalculator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class DpsCalculatorPanel extends PluginPanel
{
	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final DpsCalculatorConfig config;
	private final NpcStatsManager npcStatsManager;

	// UI Components - NPC Section
	private final JLabel npcNameLabel;
	private final JLabel npcHpLabel;
	private final JLabel npcDefLabel;
	private final JLabel npcDefBonusLabel;

	// UI Components - Player Section
	private final JLabel attackLevelLabel;
	private final JLabel strengthLevelLabel;
	private final JLabel rangedLevelLabel;
	private final JLabel magicLevelLabel;
	private final JLabel stabBonusLabel;
	private final JLabel slashBonusLabel;
	private final JLabel crushBonusLabel;
	private final JLabel strengthBonusLabel;
	private final JLabel rangedAttackLabel;
	private final JLabel rangedStrLabel;
	private final JLabel magicAttackLabel;
	private final JLabel magicDamageLabel;

	// UI Components - DPS Comparison Section (dynamic)
	private final JPanel dpsComparisonPanel;
	private final JPanel wrapper;

	// Current state
	private NpcStats currentNpc;
	private int currentWeaponSpeed = 4;
	private int currentStabBonus = 0;
	private int currentSlashBonus = 0;
	private int currentCrushBonus = 0;
	private int currentRangedAttack = 0;
	private int currentRangedStr = 0;
	private int currentMagicAttack = 0;
	private double currentMagicDamageBonus = 0;
	private int currentStrengthBonus = 0;
	private CombatType currentCombatType = CombatType.MELEE;

	private enum CombatType
	{
		MELEE, RANGED, MAGIC
	}

	@Inject
	public DpsCalculatorPanel(
		Client client,
		ClientThread clientThread,
		ItemManager itemManager,
		DpsCalculatorConfig config,
		NpcStatsManager npcStatsManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.config = config;
		this.npcStatsManager = npcStatsManager;

		// Use BorderLayout for the main panel
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Wrapper panel with proper padding
		wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(new EmptyBorder(8, 8, 8, 8));

		// Title
		JPanel titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titlePanel.setBorder(new EmptyBorder(0, 0, 8, 0));
		JLabel titleLabel = new JLabel("DPS Calculator");
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setHorizontalAlignment(JLabel.CENTER);
		titlePanel.add(titleLabel, BorderLayout.CENTER);
		wrapper.add(titlePanel);

		// Instructions
		JPanel instructionPanel = new JPanel(new BorderLayout());
		instructionPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		instructionPanel.setBorder(new EmptyBorder(0, 0, 12, 0));
		JLabel instructionLabel = new JLabel("<html><center>Right-click an attackable NPC<br>and select 'Calculate DPS'</center></html>");
		instructionLabel.setFont(FontManager.getRunescapeSmallFont());
		instructionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		instructionLabel.setHorizontalAlignment(JLabel.CENTER);
		instructionPanel.add(instructionLabel, BorderLayout.CENTER);
		wrapper.add(instructionPanel);

		// === NPC Info Section ===
		JPanel npcSection = createSection("Target NPC");
		npcNameLabel = new JLabel("None selected");
		npcHpLabel = new JLabel("-");
		npcDefLabel = new JLabel("-");
		npcDefBonusLabel = new JLabel("-");

		JPanel npcContent = new JPanel(new GridLayout(0, 2, 4, 2));
		npcContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		addLabelPair(npcContent, "Name:", npcNameLabel, Color.WHITE);
		addLabelPair(npcContent, "Hitpoints:", npcHpLabel, Color.WHITE);
		addLabelPair(npcContent, "Defence:", npcDefLabel, Color.WHITE);
		addLabelPair(npcContent, "Def Bonus:", npcDefBonusLabel, Color.WHITE);
		npcSection.add(npcContent, BorderLayout.CENTER);
		wrapper.add(npcSection);
		wrapper.add(Box.createVerticalStrut(8));

		// === Player Stats Section ===
		JPanel playerSection = createSection("Your Stats");
		attackLevelLabel = new JLabel("-");
		strengthLevelLabel = new JLabel("-");
		rangedLevelLabel = new JLabel("-");
		magicLevelLabel = new JLabel("-");
		stabBonusLabel = new JLabel("-");
		slashBonusLabel = new JLabel("-");
		crushBonusLabel = new JLabel("-");
		strengthBonusLabel = new JLabel("-");
		rangedAttackLabel = new JLabel("-");
		rangedStrLabel = new JLabel("-");
		magicAttackLabel = new JLabel("-");
		magicDamageLabel = new JLabel("-");

		JPanel playerContent = new JPanel(new GridLayout(0, 2, 4, 2));
		playerContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		addLabelPair(playerContent, "Attack:", attackLevelLabel, Color.WHITE);
		addLabelPair(playerContent, "Strength:", strengthLevelLabel, Color.WHITE);
		addLabelPair(playerContent, "Ranged:", rangedLevelLabel, Color.WHITE);
		addLabelPair(playerContent, "Magic:", magicLevelLabel, Color.WHITE);
		addLabelPair(playerContent, "Stab Bonus:", stabBonusLabel, new Color(180, 180, 180));
		addLabelPair(playerContent, "Slash Bonus:", slashBonusLabel, new Color(180, 180, 180));
		addLabelPair(playerContent, "Crush Bonus:", crushBonusLabel, new Color(180, 180, 180));
		addLabelPair(playerContent, "Str Bonus:", strengthBonusLabel, new Color(180, 180, 180));
		addLabelPair(playerContent, "Range Attack:", rangedAttackLabel, new Color(180, 180, 180));
		addLabelPair(playerContent, "Range Str:", rangedStrLabel, new Color(180, 180, 180));
		addLabelPair(playerContent, "Magic Attack:", magicAttackLabel, new Color(180, 180, 180));
		addLabelPair(playerContent, "Magic Dmg %:", magicDamageLabel, new Color(180, 180, 180));
		playerSection.add(playerContent, BorderLayout.CENTER);
		wrapper.add(playerSection);
		wrapper.add(Box.createVerticalStrut(8));

		// === DPS Comparison Section (dynamic content) ===
		dpsComparisonPanel = new JPanel(new BorderLayout());
		dpsComparisonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		dpsComparisonPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(6, 8, 6, 8)
		));
		JLabel dpsTitle = new JLabel("DPS by Attack Style");
		dpsTitle.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		dpsTitle.setForeground(ColorScheme.BRAND_ORANGE);
		dpsTitle.setBorder(new EmptyBorder(0, 0, 4, 0));
		dpsComparisonPanel.add(dpsTitle, BorderLayout.NORTH);
		wrapper.add(dpsComparisonPanel);
		wrapper.add(Box.createVerticalStrut(12));

		// Recalculate button
		JPanel buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton recalculateButton = new JButton("Recalculate");
		recalculateButton.setFocusPainted(false);
		recalculateButton.addActionListener(e -> refreshPlayerStats());
		buttonPanel.add(recalculateButton, BorderLayout.CENTER);
		wrapper.add(buttonPanel);

		add(wrapper, BorderLayout.NORTH);
	}

	private JPanel createSection(String title)
	{
		JPanel section = new JPanel(new BorderLayout());
		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		section.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(6, 8, 6, 8)
		));

		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
		titleLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
		section.add(titleLabel, BorderLayout.NORTH);

		return section;
	}

	private void addLabelPair(JPanel panel, String labelText, JLabel valueLabel, Color valueColor)
	{
		JLabel label = new JLabel(labelText);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		valueLabel.setFont(FontManager.getRunescapeSmallFont());
		valueLabel.setForeground(valueColor);
		valueLabel.setHorizontalAlignment(JLabel.RIGHT);

		panel.add(label);
		panel.add(valueLabel);
	}

	/**
	 * Set the target NPC and update display
	 */
	public void setTargetNpc(String name, int id, int combatLevel, NpcStats stats)
	{
		this.currentNpc = stats;

		npcNameLabel.setText(name + " (Lvl " + combatLevel + ")");
		npcHpLabel.setText(String.valueOf(stats.getHitpoints()));
		npcDefLabel.setText(String.valueOf(stats.getDefenceLevel()));
		npcDefBonusLabel.setText(String.format("%d/%d/%d",
			stats.getDefenceStab(),
			stats.getDefenceSlash(),
			stats.getDefenceCrush()
		));

		if (!npcStatsManager.hasAccurateStats(name))
		{
			npcNameLabel.setForeground(Color.YELLOW);
			npcNameLabel.setToolTipText("Stats are estimated - not in database");
		}
		else
		{
			npcNameLabel.setForeground(Color.WHITE);
			npcNameLabel.setToolTipText(null);
		}

		calculateAllDps();
	}

	/**
	 * Refresh player stats from game
	 */
	public void refreshPlayerStats()
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}

			int attack = client.getRealSkillLevel(Skill.ATTACK);
			int strength = client.getRealSkillLevel(Skill.STRENGTH);
			int ranged = client.getRealSkillLevel(Skill.RANGED);
			int magic = client.getRealSkillLevel(Skill.MAGIC);

			ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
			calculateEquipmentStats(equipment);

			javax.swing.SwingUtilities.invokeLater(() ->
			{
				attackLevelLabel.setText(String.valueOf(attack));
				strengthLevelLabel.setText(String.valueOf(strength));
				rangedLevelLabel.setText(String.valueOf(ranged));
				magicLevelLabel.setText(String.valueOf(magic));

				stabBonusLabel.setText(String.valueOf(currentStabBonus));
				slashBonusLabel.setText(String.valueOf(currentSlashBonus));
				crushBonusLabel.setText(String.valueOf(currentCrushBonus));
				strengthBonusLabel.setText(String.valueOf(currentStrengthBonus));
				rangedAttackLabel.setText(String.valueOf(currentRangedAttack));
				rangedStrLabel.setText(String.valueOf(currentRangedStr));
				magicAttackLabel.setText(String.valueOf(currentMagicAttack));
				magicDamageLabel.setText(String.format("%.1f%%", currentMagicDamageBonus));

				calculateAllDps();
			});
		});
	}

	/**
	 * Calculate equipment bonuses from worn items
	 */
	private void calculateEquipmentStats(ItemContainer equipment)
	{
		int stabBonus = 0, slashBonus = 0, crushBonus = 0;
		int strengthBonus = 0;
		int rangedAttack = 0, rangedStrength = 0;
		int magicAttack = 0;
		double magicDamageBonus = 0;
		currentWeaponSpeed = 4;

		if (equipment == null)
		{
			currentStabBonus = 0;
			currentSlashBonus = 0;
			currentCrushBonus = 0;
			currentStrengthBonus = 0;
			currentRangedAttack = 0;
			currentRangedStr = 0;
			currentMagicAttack = 0;
			currentMagicDamageBonus = 0;
			currentCombatType = CombatType.MELEE;
			return;
		}

		int weaponRangedAttack = 0;
		int weaponMagicAttack = 0;

		for (Item item : equipment.getItems())
		{
			if (item == null || item.getId() == -1)
			{
				continue;
			}

			ItemStats itemStats = itemManager.getItemStats(item.getId());
			if (itemStats == null || !itemStats.isEquipable() || itemStats.getEquipment() == null)
			{
				continue;
			}

			ItemEquipmentStats stats = itemStats.getEquipment();

			stabBonus += stats.getAstab();
			slashBonus += stats.getAslash();
			crushBonus += stats.getAcrush();
			strengthBonus += stats.getStr();
			rangedAttack += stats.getArange();
			rangedStrength += stats.getRstr();
			magicAttack += stats.getAmagic();
			magicDamageBonus += stats.getMdmg();

			if (stats.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx())
			{
				if (stats.getAspeed() > 0)
				{
					currentWeaponSpeed = stats.getAspeed();
				}
				weaponRangedAttack = stats.getArange();
				weaponMagicAttack = stats.getAmagic();
			}
		}

		currentStabBonus = stabBonus;
		currentSlashBonus = slashBonus;
		currentCrushBonus = crushBonus;
		currentStrengthBonus = strengthBonus;
		currentRangedAttack = rangedAttack;
		currentRangedStr = rangedStrength;
		currentMagicAttack = magicAttack;
		currentMagicDamageBonus = magicDamageBonus;

		if (weaponRangedAttack > 0 && weaponRangedAttack >= weaponMagicAttack)
		{
			currentCombatType = CombatType.RANGED;
		}
		else if (weaponMagicAttack > 0)
		{
			currentCombatType = CombatType.MAGIC;
		}
		else
		{
			currentCombatType = CombatType.MELEE;
		}
	}

	/**
	 * Calculate and display DPS for all attack styles
	 */
	private void calculateAllDps()
	{
		// Remove old content
		if (dpsComparisonPanel.getComponentCount() > 1)
		{
			dpsComparisonPanel.remove(1);
		}

		if (currentNpc == null)
		{
			JLabel noNpc = new JLabel("Select an NPC first");
			noNpc.setFont(FontManager.getRunescapeSmallFont());
			noNpc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			dpsComparisonPanel.add(noNpc, BorderLayout.CENTER);
			dpsComparisonPanel.revalidate();
			dpsComparisonPanel.repaint();
			return;
		}

		JPanel resultsContent = new JPanel();
		resultsContent.setLayout(new BoxLayout(resultsContent, BoxLayout.Y_AXIS));
		resultsContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		int attackLevel = parseOrDefault(attackLevelLabel.getText(), 99);
		int strengthLevel = parseOrDefault(strengthLevelLabel.getText(), 99);
		int rangedLevel = parseOrDefault(rangedLevelLabel.getText(), 99);
		int magicLevel = parseOrDefault(magicLevelLabel.getText(), 99);

		double bestDps = 0;
		String bestStyle = "";

		try
		{
			switch (currentCombatType)
			{
				case RANGED:
					// Show ranged styles
					DpsCalculator.DpsResult accurateResult = DpsCalculator.calculateRangedDps(
						rangedLevel, currentRangedAttack, currentRangedStr,
						currentWeaponSpeed, currentNpc,
						config.rangedPrayer(), config.potionBoost(), config.onSlayerTask(),
						false, 1.0
					);
					// Rapid is 1 tick faster
					DpsCalculator.DpsResult rapidResult = DpsCalculator.calculateRangedDps(
						rangedLevel, currentRangedAttack, currentRangedStr,
						Math.max(1, currentWeaponSpeed - 1), currentNpc,
						config.rangedPrayer(), config.potionBoost(), config.onSlayerTask(),
						false, 1.0
					);
					DpsCalculator.DpsResult longrangeResult = DpsCalculator.calculateRangedDps(
						rangedLevel, currentRangedAttack, currentRangedStr,
						currentWeaponSpeed, currentNpc,
						config.rangedPrayer(), config.potionBoost(), config.onSlayerTask(),
						false, 1.0
					);

					bestDps = Math.max(accurateResult.getDps(), Math.max(rapidResult.getDps(), longrangeResult.getDps()));
					if (rapidResult.getDps() >= bestDps) bestStyle = "Rapid";
					else if (accurateResult.getDps() >= bestDps) bestStyle = "Accurate";
					else bestStyle = "Longrange";

					addDpsRow(resultsContent, "Accurate", accurateResult, bestStyle.equals("Accurate"));
					addDpsRow(resultsContent, "Rapid", rapidResult, bestStyle.equals("Rapid"));
					addDpsRow(resultsContent, "Longrange", longrangeResult, bestStyle.equals("Longrange"));
					break;

				case MAGIC:
					// Show magic style
					DpsCalculator.DpsResult magicResult = DpsCalculator.calculateMagicDps(
						magicLevel, currentMagicAttack, 30, currentMagicDamageBonus, 5, currentNpc,
						config.magicPrayer(), config.potionBoost(), config.onSlayerTask(),
						false
					);
					addDpsRow(resultsContent, "Casting", magicResult, true);
					break;

				default: // MELEE
					// Calculate DPS for each attack type
					DpsCalculator.DpsResult stabResult = DpsCalculator.calculateMeleeDps(
						attackLevel, strengthLevel, currentStabBonus, currentStrengthBonus,
						currentWeaponSpeed, DpsCalculator.CombatStyle.STAB, currentNpc,
						config.meleePrayer(), config.potionBoost(), config.onSlayerTask(),
						false, 1.0
					);
					DpsCalculator.DpsResult slashResult = DpsCalculator.calculateMeleeDps(
						attackLevel, strengthLevel, currentSlashBonus, currentStrengthBonus,
						currentWeaponSpeed, DpsCalculator.CombatStyle.SLASH, currentNpc,
						config.meleePrayer(), config.potionBoost(), config.onSlayerTask(),
						false, 1.0
					);
					DpsCalculator.DpsResult crushResult = DpsCalculator.calculateMeleeDps(
						attackLevel, strengthLevel, currentCrushBonus, currentStrengthBonus,
						currentWeaponSpeed, DpsCalculator.CombatStyle.CRUSH, currentNpc,
						config.meleePrayer(), config.potionBoost(), config.onSlayerTask(),
						false, 1.0
					);

					bestDps = Math.max(stabResult.getDps(), Math.max(slashResult.getDps(), crushResult.getDps()));
					if (stabResult.getDps() >= bestDps) bestStyle = "Stab";
					else if (slashResult.getDps() >= bestDps) bestStyle = "Slash";
					else bestStyle = "Crush";

					addDpsRow(resultsContent, "Stab", stabResult, bestStyle.equals("Stab"));
					addDpsRow(resultsContent, "Slash", slashResult, bestStyle.equals("Slash"));
					addDpsRow(resultsContent, "Crush", crushResult, bestStyle.equals("Crush"));
					break;
			}

			// Add time to kill for best style
			resultsContent.add(Box.createVerticalStrut(8));
			JPanel ttkPanel = new JPanel(new GridLayout(1, 2, 4, 0));
			ttkPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			JLabel ttkLabel = new JLabel("Best Kill Time:");
			ttkLabel.setFont(FontManager.getRunescapeSmallFont());
			ttkLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			JLabel ttkValue = new JLabel(String.format("~%s (%s)", formatTime(currentNpc.getHitpoints() / (bestDps > 0 ? bestDps : 1)), bestStyle));
			ttkValue.setFont(FontManager.getRunescapeSmallFont());
			ttkValue.setForeground(Color.YELLOW);
			ttkValue.setHorizontalAlignment(JLabel.RIGHT);
			ttkPanel.add(ttkLabel);
			ttkPanel.add(ttkValue);
			resultsContent.add(ttkPanel);
		}
		catch (Exception e)
		{
			log.error("Error calculating DPS", e);
			JLabel errorLabel = new JLabel("Error calculating DPS");
			errorLabel.setFont(FontManager.getRunescapeSmallFont());
			errorLabel.setForeground(Color.RED);
			resultsContent.add(errorLabel);
		}

		dpsComparisonPanel.add(resultsContent, BorderLayout.CENTER);
		dpsComparisonPanel.revalidate();
		dpsComparisonPanel.repaint();
	}

	private void addDpsRow(JPanel panel, String styleName, DpsCalculator.DpsResult result, boolean isBest)
	{
		JPanel row = new JPanel(new GridLayout(1, 4, 4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));

		// Style name
		JLabel nameLabel = new JLabel(styleName);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setForeground(isBest ? new Color(100, 255, 100) : Color.WHITE);

		// DPS
		JLabel dpsLabel = new JLabel(String.format("%.2f", result.getDps()));
		dpsLabel.setFont(FontManager.getRunescapeSmallFont());
		dpsLabel.setForeground(isBest ? new Color(100, 255, 100) : new Color(200, 200, 200));
		dpsLabel.setHorizontalAlignment(JLabel.RIGHT);

		// Max hit
		JLabel maxHitLabel = new JLabel("Max: " + result.getMaxHit());
		maxHitLabel.setFont(FontManager.getRunescapeSmallFont());
		maxHitLabel.setForeground(new Color(150, 150, 150));
		maxHitLabel.setHorizontalAlignment(JLabel.RIGHT);

		// Accuracy
		JLabel accLabel = new JLabel(String.format("%.0f%%", result.getAccuracy()));
		accLabel.setFont(FontManager.getRunescapeSmallFont());
		accLabel.setForeground(new Color(150, 150, 150));
		accLabel.setHorizontalAlignment(JLabel.RIGHT);

		row.add(nameLabel);
		row.add(dpsLabel);
		row.add(maxHitLabel);
		row.add(accLabel);

		panel.add(row);
	}

	private int parseOrDefault(String text, int defaultValue)
	{
		if (text == null || text.equals("-") || text.isEmpty())
		{
			return defaultValue;
		}
		try
		{
			return Integer.parseInt(text.replaceAll("[^0-9-]", ""));
		}
		catch (NumberFormatException e)
		{
			return defaultValue;
		}
	}

	private String formatTime(double seconds)
	{
		if (seconds < 60)
		{
			return String.format("%.1fs", seconds);
		}
		int mins = (int) (seconds / 60);
		int secs = (int) (seconds % 60);
		return String.format("%dm %ds", mins, secs);
	}
}

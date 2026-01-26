/*
 * Copyright (c) 2024
 * All rights reserved.
 */
package net.runelite.client.plugins.dpscalculator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.api.StructComposition;
import net.runelite.api.ItemID;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.Text;

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
	private List<CombatOption> currentCombatOptions = Collections.emptyList();
	private boolean currentHasAutocastOption = false;
	private boolean currentHasMagicOption = false;
	private boolean currentIsPoweredStaff = false;
	private int currentWeaponId = -1;

	private enum CombatType
	{
		MELEE, RANGED, MAGIC
	}

	private enum AttackCategory
	{
		STAB, SLASH, CRUSH, RANGED, MAGIC
	}

	private enum WeaponStyleType
	{
		ACCURATE,
		AGGRESSIVE,
		CONTROLLED,
		DEFENSIVE,
		RANGING,
		LONGRANGE,
		CASTING,
		DEFENSIVE_CASTING,
		OTHER
	}

	private enum RangedStyle
	{
		ACCURATE("Accurate"),
		RAPID("Rapid"),
		LONGRANGE("Longrange");

		private final String label;

		RangedStyle(String label)
		{
			this.label = label;
		}

		String getLabel()
		{
			return label;
		}
	}

	private static final class CombatOption
	{
		private final AttackCategory attackCategory;
		private final WeaponStyleType styleType;
		private final RangedStyle rangedStyle;

		private CombatOption(AttackCategory attackCategory, WeaponStyleType styleType, RangedStyle rangedStyle)
		{
			this.attackCategory = attackCategory;
			this.styleType = styleType;
			this.rangedStyle = rangedStyle;
		}
	}

	private static final class ResultRow
	{
		private final String label;
		private final DpsCalculator.DpsResult result;

		private ResultRow(String label, DpsCalculator.DpsResult result)
		{
			this.label = label;
			this.result = result;
		}
	}

	private static final class WeaponCategorySpec
	{
		private final AttackCategory[] attackCategories;
		private final RangedStyle[] rangedStyles;
		private final boolean autocast;
		private final boolean powered;

		private WeaponCategorySpec(AttackCategory[] attackCategories, RangedStyle[] rangedStyles, boolean autocast, boolean powered)
		{
			this.attackCategories = attackCategories;
			this.rangedStyles = rangedStyles;
			this.autocast = autocast;
			this.powered = powered;
		}
	}

	private static final Map<Integer, WeaponCategorySpec> WEAPON_CATEGORY_SPECS = createWeaponCategorySpecs();

	private static Map<Integer, WeaponCategorySpec> createWeaponCategorySpecs()
	{
		Map<Integer, WeaponCategorySpec> specs = new HashMap<>();

		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_UNARMED,
			spec(AttackCategory.CRUSH, AttackCategory.CRUSH, AttackCategory.CRUSH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_AXE,
			spec(AttackCategory.SLASH, AttackCategory.SLASH, AttackCategory.CRUSH, AttackCategory.SLASH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_BLUNT,
			spec(AttackCategory.CRUSH, AttackCategory.CRUSH, AttackCategory.CRUSH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_BLUNT_BLUDGEON,
			spec(AttackCategory.CRUSH, AttackCategory.CRUSH, AttackCategory.CRUSH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_BOW,
			rangedSpec(RangedStyle.ACCURATE, RangedStyle.RAPID, RangedStyle.LONGRANGE));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_CLAW,
			spec(AttackCategory.SLASH, AttackCategory.SLASH, AttackCategory.STAB, AttackCategory.SLASH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_CROSSBOW,
			rangedSpec(RangedStyle.ACCURATE, RangedStyle.RAPID, RangedStyle.LONGRANGE));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_FLAMER,
			spec(new AttackCategory[]{AttackCategory.SLASH, AttackCategory.RANGED, AttackCategory.MAGIC},
				new RangedStyle[]{null, RangedStyle.ACCURATE, null}, false, false));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_GRENADE,
			spec(new AttackCategory[]{AttackCategory.RANGED, AttackCategory.RANGED, AttackCategory.RANGED},
				new RangedStyle[]{RangedStyle.ACCURATE, RangedStyle.RAPID, RangedStyle.LONGRANGE}, false, false));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_GUN,
			spec(new AttackCategory[]{null, AttackCategory.CRUSH}, null, false, false));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_HACKSWORD,
			spec(AttackCategory.SLASH, AttackCategory.SLASH, AttackCategory.STAB, AttackCategory.SLASH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_HEAVYSWORD,
			spec(AttackCategory.SLASH, AttackCategory.SLASH, AttackCategory.CRUSH, AttackCategory.SLASH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_HEAVYSWORD_LARGE,
			spec(AttackCategory.SLASH, AttackCategory.SLASH, AttackCategory.CRUSH, AttackCategory.SLASH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_PICKAXE,
			spec(AttackCategory.STAB, AttackCategory.STAB, AttackCategory.CRUSH, AttackCategory.STAB));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_POLEARM,
			spec(AttackCategory.STAB, AttackCategory.SLASH, AttackCategory.STAB));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_CHARGESPEAR,
			spec(AttackCategory.STAB, AttackCategory.SLASH, AttackCategory.CRUSH, AttackCategory.STAB));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_POLESTAFF,
			spec(AttackCategory.CRUSH, AttackCategory.CRUSH, AttackCategory.CRUSH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_SCYTHE,
			spec(AttackCategory.SLASH, AttackCategory.SLASH, AttackCategory.CRUSH, AttackCategory.SLASH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_SPEAR,
			spec(AttackCategory.STAB, AttackCategory.SLASH, AttackCategory.CRUSH, AttackCategory.STAB));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_BANNER,
			spec(AttackCategory.STAB, AttackCategory.SLASH, AttackCategory.CRUSH, AttackCategory.STAB));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_SPIKED,
			spec(AttackCategory.CRUSH, AttackCategory.CRUSH, AttackCategory.STAB, AttackCategory.CRUSH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_STABSWORD,
			spec(AttackCategory.STAB, AttackCategory.STAB, AttackCategory.SLASH, AttackCategory.STAB));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_STAFF,
			spec(new AttackCategory[]{AttackCategory.CRUSH, AttackCategory.CRUSH, AttackCategory.CRUSH, AttackCategory.MAGIC},
				null, true, false));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_STAFF_BLADED,
			spec(new AttackCategory[]{AttackCategory.STAB, AttackCategory.SLASH, AttackCategory.CRUSH, AttackCategory.MAGIC},
				null, true, false));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_STAFF_SPELLBLADE,
			spec(new AttackCategory[]{AttackCategory.STAB, AttackCategory.SLASH, AttackCategory.CRUSH, AttackCategory.MAGIC},
				null, true, false));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_THROWN,
			rangedSpec(RangedStyle.ACCURATE, RangedStyle.RAPID, RangedStyle.LONGRANGE));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_WHIP,
			spec(AttackCategory.SLASH, AttackCategory.SLASH, AttackCategory.SLASH));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_STAFF_SELFPOWERING,
			spec(new AttackCategory[]{AttackCategory.MAGIC, AttackCategory.MAGIC, AttackCategory.MAGIC}, null, false, true));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_WAND_SELFPOWERING,
			spec(new AttackCategory[]{AttackCategory.MAGIC, AttackCategory.MAGIC, AttackCategory.MAGIC}, null, false, true));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_BULWARK,
			spec(new AttackCategory[]{AttackCategory.CRUSH, null}, null, false, false));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_PARTISAN,
			spec(AttackCategory.STAB, AttackCategory.STAB, AttackCategory.CRUSH, AttackCategory.STAB));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_TRIBRID,
			spec(new AttackCategory[]{AttackCategory.STAB, AttackCategory.RANGED, AttackCategory.MAGIC},
				new RangedStyle[]{null, RangedStyle.RAPID, null}, false, false));
		specs.put(DBTableID.CombatInterfaceWeaponCategory.Row.COMBAT_INTERFACE_EGG,
			spec(AttackCategory.CRUSH, AttackCategory.CRUSH, AttackCategory.CRUSH));

		return specs;
	}

	private static WeaponCategorySpec spec(AttackCategory... categories)
	{
		return new WeaponCategorySpec(categories, null, false, false);
	}

	private static WeaponCategorySpec spec(AttackCategory[] categories, RangedStyle[] rangedStyles, boolean autocast, boolean powered)
	{
		return new WeaponCategorySpec(categories, rangedStyles, autocast, powered);
	}

	private static WeaponCategorySpec rangedSpec(RangedStyle... rangedStyles)
	{
		AttackCategory[] categories = new AttackCategory[rangedStyles.length];
		for (int i = 0; i < categories.length; i++)
		{
			categories[i] = AttackCategory.RANGED;
		}
		return new WeaponCategorySpec(categories, rangedStyles, false, false);
	}

	private static final Set<Integer> POWERED_STAFF_IDS = Set.of(
		ItemID.TRIDENT_OF_THE_SEAS,
		ItemID.TRIDENT_OF_THE_SEAS_FULL,
		ItemID.TRIDENT_OF_THE_SEAS_E,
		ItemID.UNCHARGED_TRIDENT,
		ItemID.UNCHARGED_TRIDENT_E,
		ItemID.TRIDENT_OF_THE_SWAMP,
		ItemID.TRIDENT_OF_THE_SWAMP_E,
		ItemID.UNCHARGED_TOXIC_TRIDENT,
		ItemID.UNCHARGED_TOXIC_TRIDENT_E,
		ItemID.WARPED_SCEPTRE,
		ItemID.WARPED_SCEPTRE_UNCHARGED,
		ItemID.EYE_OF_AYAK,
		ItemID.EYE_OF_AYAK_UNCHARGED,
		ItemID.SANGUINESTI_STAFF,
		ItemID.SANGUINESTI_STAFF_UNCHARGED,
		ItemID.HOLY_SANGUINESTI_STAFF,
		ItemID.HOLY_SANGUINESTI_STAFF_UNCHARGED,
		ItemID.TUMEKENS_SHADOW,
		ItemID.TUMEKENS_SHADOW_UNCHARGED,
		ItemID.CORRUPTED_TUMEKENS_SHADOW,
		ItemID.CORRUPTED_TUMEKENS_SHADOW_UNCHARGED,
		ItemID.DAWNBRINGER,
		ItemID.ACCURSED_SCEPTRE,
		ItemID.ACCURSED_SCEPTRE_U,
		ItemID.ACCURSED_SCEPTRE_A,
		ItemID.ACCURSED_SCEPTRE_AU,
		ItemID.BONE_STAFF,
		ItemID.CORRUPTED_STAFF_BASIC,
		ItemID.CORRUPTED_STAFF_ATTUNED,
		ItemID.CORRUPTED_STAFF_PERFECTED,
		ItemID.CRYSTAL_STAFF_BASIC,
		ItemID.CRYSTAL_STAFF_ATTUNED,
		ItemID.CRYSTAL_STAFF_PERFECTED,
		ItemID.STARTER_STAFF,
		ItemID.STARTER_STAFF_28557,
		ItemID.THAMMARONS_SCEPTRE,
		ItemID.THAMMARONS_SCEPTRE_U,
		ItemID.THAMMARONS_SCEPTRE_A,
		ItemID.THAMMARONS_SCEPTRE_AU
	);

	private static final int[] COMBAT_STYLE_ICON_IDS =
	{
		InterfaceID.CombatInterface._0_ICON,
		InterfaceID.CombatInterface._1_ICON,
		InterfaceID.CombatInterface._2_ICON,
		InterfaceID.CombatInterface._3_ICON
	};

	private static final int[] COMBAT_STYLE_TEXT_IDS =
	{
		InterfaceID.CombatInterface._0_TEXT,
		InterfaceID.CombatInterface._1_TEXT,
		InterfaceID.CombatInterface._2_TEXT,
		InterfaceID.CombatInterface._3_TEXT
	};

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
		currentWeaponId = -1;

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
			currentWeaponId = -1;
			currentCombatType = CombatType.MELEE;
			currentCombatOptions = Collections.emptyList();
			currentHasAutocastOption = false;
			currentHasMagicOption = false;
			currentIsPoweredStaff = false;
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
				currentWeaponId = item.getId();
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

		updateCombatOptions();
	}

	private void updateCombatOptions()
	{
		List<CombatOption> options = new ArrayList<>();
		boolean hasMagicOption = false;
		boolean hasAutocastOption = false;
		boolean isPoweredStaff = false;

		int weaponType = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
		WeaponStyleType[] styleTypes = getWeaponTypeStyles(weaponType);
		WeaponCategorySpec spec = getWeaponCategorySpec(weaponType);

		if (spec != null)
		{
			hasAutocastOption = spec.autocast;
			isPoweredStaff = spec.powered || POWERED_STAFF_IDS.contains(currentWeaponId);

			int slotCount = Math.min(styleTypes.length, spec.attackCategories.length);
			for (int i = 0; i < slotCount; i++)
			{
				AttackCategory attackCategory = spec.attackCategories[i];
				if (attackCategory == null)
				{
					continue;
				}

				WeaponStyleType styleType = styleTypes[i];
				if (styleType == WeaponStyleType.OTHER)
				{
					continue;
				}

				RangedStyle rangedStyle = null;
				if (attackCategory == AttackCategory.RANGED)
				{
					if (spec.rangedStyles != null && i < spec.rangedStyles.length)
					{
						rangedStyle = spec.rangedStyles[i];
					}
					if (rangedStyle == null)
					{
						rangedStyle = defaultRangedStyleForIndex(i);
					}
				}

				if (attackCategory == AttackCategory.MAGIC)
				{
					hasMagicOption = true;
				}

				options.add(new CombatOption(attackCategory, styleType, rangedStyle));
			}
		}
		else
		{
			for (int i = 0; i < COMBAT_STYLE_ICON_IDS.length; i++)
			{
				WeaponStyleType styleType = i < styleTypes.length ? styleTypes[i] : WeaponStyleType.OTHER;
				if (styleType == WeaponStyleType.OTHER)
				{
					continue;
				}

				Widget textWidget = client.getWidget(COMBAT_STYLE_TEXT_IDS[i]);
				Widget iconWidget = client.getWidget(COMBAT_STYLE_ICON_IDS[i]);
				String label = textWidget != null ? Text.removeTags(textWidget.getText()).trim() : "";
				int spriteId = iconWidget != null ? iconWidget.getSpriteId() : -1;

				if ((label == null || label.isEmpty()) && spriteId <= 0)
				{
					continue;
				}

				AttackCategory attackCategory = getAttackCategoryFromSprite(spriteId);
				if (attackCategory == null)
				{
					attackCategory = getAttackCategoryFromStyleType(styleType);
				}
				if (attackCategory == null)
				{
					attackCategory = getAttackCategoryFromLabel(label);
				}
				if (attackCategory == null)
				{
					continue;
				}

				RangedStyle rangedStyle = null;
				if (attackCategory == AttackCategory.RANGED)
				{
					rangedStyle = getRangedStyleFromSprite(spriteId);
					if (rangedStyle == null)
					{
						rangedStyle = getRangedStyleFromLabel(label);
					}
					if (rangedStyle == null && styleType == WeaponStyleType.LONGRANGE)
					{
						rangedStyle = RangedStyle.LONGRANGE;
					}
					if (rangedStyle == null)
					{
						rangedStyle = RangedStyle.ACCURATE;
					}
				}

				if (attackCategory == AttackCategory.MAGIC)
				{
					hasMagicOption = true;
				}

				options.add(new CombatOption(attackCategory, styleType, rangedStyle));
			}

			for (int i = 4; i < styleTypes.length; i++)
			{
				if (styleTypes[i] == WeaponStyleType.CASTING || styleTypes[i] == WeaponStyleType.DEFENSIVE_CASTING)
				{
					hasMagicOption = true;
					break;
				}
			}

			Widget autocastWidget = client.getWidget(InterfaceID.CombatInterface.AUTOCAST_BUTTONS);
			hasAutocastOption = autocastWidget != null && !autocastWidget.isHidden();
			isPoweredStaff = POWERED_STAFF_IDS.contains(currentWeaponId);
			if (isPoweredStaff)
			{
				hasMagicOption = true;
			}
		}

		currentCombatOptions = options;
		currentHasMagicOption = hasMagicOption;
		currentHasAutocastOption = hasAutocastOption;
		currentIsPoweredStaff = isPoweredStaff;
	}

	private WeaponCategorySpec getWeaponCategorySpec(int weaponType)
	{
		List<Integer> rows = client.getDBRowsByValue(
			DBTableID.CombatInterfaceWeaponCategory.ID,
			DBTableID.CombatInterfaceWeaponCategory.COL_ID,
			0,
			weaponType
		);
		if (rows == null || rows.isEmpty())
		{
			return null;
		}

		return WEAPON_CATEGORY_SPECS.get(rows.get(0));
	}

	private static RangedStyle defaultRangedStyleForIndex(int index)
	{
		switch (index)
		{
			case 1:
				return RangedStyle.RAPID;
			case 2:
				return RangedStyle.LONGRANGE;
			default:
				return RangedStyle.ACCURATE;
		}
	}

	private WeaponStyleType[] getWeaponTypeStyles(int weaponType)
	{
		int weaponStyleEnum = client.getEnum(EnumID.WEAPON_STYLES).getIntValue(weaponType);
		if (weaponStyleEnum == -1)
		{
			if (weaponType == 22)
			{
				return new WeaponStyleType[]{
					WeaponStyleType.ACCURATE,
					WeaponStyleType.AGGRESSIVE,
					WeaponStyleType.OTHER,
					WeaponStyleType.DEFENSIVE,
					WeaponStyleType.CASTING,
					WeaponStyleType.DEFENSIVE_CASTING
				};
			}

			if (weaponType == 30)
			{
				return new WeaponStyleType[]{
					WeaponStyleType.ACCURATE,
					WeaponStyleType.AGGRESSIVE,
					WeaponStyleType.AGGRESSIVE,
					WeaponStyleType.DEFENSIVE
				};
			}

			return new WeaponStyleType[0];
		}

		int[] weaponStyleStructs = client.getEnum(weaponStyleEnum).getIntVals();
		WeaponStyleType[] styles = new WeaponStyleType[weaponStyleStructs.length];

		for (int i = 0; i < weaponStyleStructs.length; i++)
		{
			StructComposition attackStyleStruct = client.getStructComposition(weaponStyleStructs[i]);
			String attackStyleName = attackStyleStruct.getStringValue(ParamID.ATTACK_STYLE_NAME);
			WeaponStyleType styleType = parseWeaponStyleType(attackStyleName);

			if (i == 5 && styleType == WeaponStyleType.DEFENSIVE)
			{
				styleType = WeaponStyleType.DEFENSIVE_CASTING;
			}

			styles[i] = styleType;
		}

		return styles;
	}

	private WeaponStyleType parseWeaponStyleType(String name)
	{
		if (name == null)
		{
			return WeaponStyleType.OTHER;
		}

		switch (name.trim().toLowerCase(Locale.ROOT))
		{
			case "accurate":
				return WeaponStyleType.ACCURATE;
			case "aggressive":
				return WeaponStyleType.AGGRESSIVE;
			case "controlled":
				return WeaponStyleType.CONTROLLED;
			case "defensive":
				return WeaponStyleType.DEFENSIVE;
			case "ranging":
				return WeaponStyleType.RANGING;
			case "longrange":
				return WeaponStyleType.LONGRANGE;
			case "casting":
				return WeaponStyleType.CASTING;
			case "defensive casting":
				return WeaponStyleType.DEFENSIVE_CASTING;
			default:
				return WeaponStyleType.OTHER;
		}
	}

	private AttackCategory getAttackCategoryFromStyleType(WeaponStyleType styleType)
	{
		if (styleType == null)
		{
			return null;
		}

		switch (styleType)
		{
			case CASTING:
			case DEFENSIVE_CASTING:
				return AttackCategory.MAGIC;
			case RANGING:
			case LONGRANGE:
				return AttackCategory.RANGED;
			default:
				return null;
		}
	}

	private AttackCategory getAttackCategoryFromSprite(int spriteId)
	{
		switch (spriteId)
		{
			case SpriteID.COMBAT_STYLE_SWORD_STAB:
			case SpriteID.COMBAT_STYLE_SPEAR_LUNGE:
			case SpriteID.COMBAT_STYLE_SPEAR_BLOCK:
			case SpriteID.COMBAT_STYLE_CLAWS_LUNGE:
			case SpriteID.COMBAT_STYLE_HALBERD_JAB:
			case SpriteID.COMBAT_STYLE_SCYTHE_JAB:
			case SpriteID.COMBAT_STYLE_PICKAXE_IMPALE:
			case SpriteID.COMBAT_STYLE_MACE_SPIKE:
			case SpriteID.COMBAT_STYLE_PICKAXE_SPIKE:
				return AttackCategory.STAB;

			case SpriteID.COMBAT_STYLE_SWORD_SLASH:
			case SpriteID.COMBAT_STYLE_SWORD_CHOP:
			case SpriteID.COMBAT_STYLE_SWORD_BLOCK:
			case SpriteID.COMBAT_STYLE_AXE_CHOP:
			case SpriteID.COMBAT_STYLE_AXE_HACK:
			case SpriteID.COMBAT_STYLE_AXE_BLOCK:
			case SpriteID.COMBAT_STYLE_SPEAR_SWIPE:
			case SpriteID.COMBAT_STYLE_SCYTHE_CHOP:
			case SpriteID.COMBAT_STYLE_SCYTHE_REAP:
			case SpriteID.COMBAT_STYLE_SCYTHE_BLOCK:
			case SpriteID.COMBAT_STYLE_HALBERD_SWIPE:
			case SpriteID.COMBAT_STYLE_HALBERD_BLOCK:
			case SpriteID.COMBAT_STYLE_CLAWS_SLASH:
			case SpriteID.COMBAT_STYLE_CLAWS_CHOP:
			case SpriteID.COMBAT_STYLE_CLAWS_BLOCK:
			case SpriteID.COMBAT_STYLE_WHIP_FLICK:
			case SpriteID.COMBAT_STYLE_WHIP_LASH:
				return AttackCategory.SLASH;

			case SpriteID.COMBAT_STYLE_AXE_SMASH:
			case SpriteID.COMBAT_STYLE_SPEAR_POUND:
			case SpriteID.COMBAT_STYLE_MACE_POUND:
			case SpriteID.COMBAT_STYLE_MACE_PUMMEL:
			case SpriteID.COMBAT_STYLE_MACE_BLOCK:
			case SpriteID.COMBAT_STYLE_UNARMED_PUNCH:
			case SpriteID.COMBAT_STYLE_UNARMED_KICK:
			case SpriteID.COMBAT_STYLE_UNARMED_BLOCK:
			case SpriteID.COMBAT_STYLE_STAFF_BASH:
			case SpriteID.COMBAT_STYLE_STAFF_POUND:
			case SpriteID.COMBAT_STYLE_STAFF_BLOCK:
			case SpriteID.COMBAT_STYLE_HAMMER_POUND:
			case SpriteID.COMBAT_STYLE_HAMMER_PUMMEL:
			case SpriteID.COMBAT_STYLE_HAMMER_BLOCK:
			case SpriteID.COMBAT_STYLE_PICKAXE_SMASH:
			case SpriteID.COMBAT_STYLE_PICKAXE_BLOCK:
			case SpriteID.COMBAT_STYLE_SALAMANDER_BLAZE:
				return AttackCategory.CRUSH;

			case SpriteID.COMBAT_STYLE_BOW_ACCURATE:
			case SpriteID.COMBAT_STYLE_BOW_RAPID:
			case SpriteID.COMBAT_STYLE_BOW_LONGRANGE:
			case SpriteID.COMBAT_STYLE_CROSSBOW_ACCURATE:
			case SpriteID.COMBAT_STYLE_CROSSBOW_RAPID:
			case SpriteID.COMBAT_STYLE_CROSSBOW_LONGRANGE:
			case SpriteID.COMBAT_STYLE_CHINCHOMPA_SHORT_FUSE:
			case SpriteID.COMBAT_STYLE_CHINCHOMPA_MEDIUM_FUSE:
			case SpriteID.COMBAT_STYLE_CHINCHOMPA_LONG_FUSE:
			case SpriteID.COMBAT_STYLE_SALAMANDER_FLARE:
				return AttackCategory.RANGED;

			case SpriteID.COMBAT_STYLE_MAGIC_ACCURATE:
			case SpriteID.COMBAT_STYLE_MAGIC_RAPID:
			case SpriteID.COMBAT_STYLE_MAGIC_LONGRANGE:
			case SpriteID.COMBAT_STYLE_SALAMANDER_SCORCH:
				return AttackCategory.MAGIC;
			default:
				return null;
		}
	}

	private RangedStyle getRangedStyleFromSprite(int spriteId)
	{
		switch (spriteId)
		{
			case SpriteID.COMBAT_STYLE_BOW_ACCURATE:
			case SpriteID.COMBAT_STYLE_CROSSBOW_ACCURATE:
				return RangedStyle.ACCURATE;
			case SpriteID.COMBAT_STYLE_BOW_RAPID:
			case SpriteID.COMBAT_STYLE_CROSSBOW_RAPID:
			case SpriteID.COMBAT_STYLE_CHINCHOMPA_SHORT_FUSE:
				return RangedStyle.RAPID;
			case SpriteID.COMBAT_STYLE_BOW_LONGRANGE:
			case SpriteID.COMBAT_STYLE_CROSSBOW_LONGRANGE:
			case SpriteID.COMBAT_STYLE_CHINCHOMPA_LONG_FUSE:
				return RangedStyle.LONGRANGE;
			case SpriteID.COMBAT_STYLE_CHINCHOMPA_MEDIUM_FUSE:
				return RangedStyle.ACCURATE;
			case SpriteID.COMBAT_STYLE_SALAMANDER_FLARE:
				return RangedStyle.ACCURATE;
			default:
				return null;
		}
	}

	private AttackCategory getAttackCategoryFromLabel(String label)
	{
		if (label == null)
		{
			return null;
		}

		String lower = label.trim().toLowerCase(Locale.ROOT);
		if (lower.isEmpty())
		{
			return null;
		}

		if (lower.contains("cast") || lower.contains("casting") || lower.contains("spell") || lower.contains("scorch") || lower.contains("focus"))
		{
			return AttackCategory.MAGIC;
		}

		if (lower.contains("rapid") || lower.contains("accurate") || lower.contains("longrange")
			|| lower.contains("long range") || lower.contains("fuse") || lower.contains("flare"))
		{
			return AttackCategory.RANGED;
		}

		if (lower.contains("stab") || lower.contains("lunge") || lower.contains("jab")
			|| lower.contains("spike") || lower.contains("impale") || lower.contains("poke"))
		{
			return AttackCategory.STAB;
		}

		if (lower.contains("slash") || lower.contains("chop") || lower.contains("hack")
			|| lower.contains("swipe") || lower.contains("lash") || lower.contains("flick")
			|| lower.contains("reap"))
		{
			return AttackCategory.SLASH;
		}

		if (lower.contains("crush") || lower.contains("smash") || lower.contains("pound")
			|| lower.contains("pummel") || lower.contains("bash") || lower.contains("punch")
			|| lower.contains("kick"))
		{
			return AttackCategory.CRUSH;
		}

		return null;
	}

	private RangedStyle getRangedStyleFromLabel(String label)
	{
		if (label == null)
		{
			return null;
		}

		String lower = label.trim().toLowerCase(Locale.ROOT);
		if (lower.isEmpty())
		{
			return null;
		}

		if (lower.contains("rapid") || lower.contains("medium fuse"))
		{
			return RangedStyle.RAPID;
		}

		if (lower.contains("longrange") || lower.contains("long range") || lower.contains("long fuse"))
		{
			return RangedStyle.LONGRANGE;
		}

		if (lower.contains("accurate") || lower.contains("short fuse") || lower.contains("flare"))
		{
			return RangedStyle.ACCURATE;
		}

		return null;
	}

	private int getStyleAttackBonus(WeaponStyleType styleType)
	{
		if (styleType == null)
		{
			return 0;
		}

		switch (styleType)
		{
			case ACCURATE:
				return 3;
			case CONTROLLED:
				return 1;
			default:
				return 0;
		}
	}

	private int getStyleStrengthBonus(WeaponStyleType styleType)
	{
		if (styleType == null)
		{
			return 0;
		}

		switch (styleType)
		{
			case AGGRESSIVE:
				return 3;
			case CONTROLLED:
				return 1;
			default:
				return 0;
		}
	}

	private int getMeleeAttackBonus(AttackCategory category)
	{
		switch (category)
		{
			case STAB:
				return currentStabBonus;
			case SLASH:
				return currentSlashBonus;
			case CRUSH:
				return currentCrushBonus;
			default:
				return 0;
		}
	}

	private DpsCalculator.AttackType toCalculatorAttackType(AttackCategory category)
	{
		switch (category)
		{
			case STAB:
				return DpsCalculator.AttackType.STAB;
			case SLASH:
				return DpsCalculator.AttackType.SLASH;
			case CRUSH:
				return DpsCalculator.AttackType.CRUSH;
			default:
				return null;
		}
	}

	private boolean shouldShowMagic()
	{
		return currentIsPoweredStaff || (currentHasMagicOption && currentHasAutocastOption);
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

		double bestDps = -1;
		String bestStyle = "";

		try
		{
			List<ResultRow> rows = new ArrayList<>();
			boolean usedCombatOptions = currentCombatOptions != null && !currentCombatOptions.isEmpty();

			if (usedCombatOptions)
			{
				EnumMap<DpsCalculator.AttackType, DpsCalculator.DpsResult> bestMelee = new EnumMap<>(DpsCalculator.AttackType.class);
				EnumSet<RangedStyle> rangedStyles = EnumSet.noneOf(RangedStyle.class);

				for (CombatOption option : currentCombatOptions)
				{
					if (option.attackCategory == AttackCategory.STAB
						|| option.attackCategory == AttackCategory.SLASH
						|| option.attackCategory == AttackCategory.CRUSH)
					{
						DpsCalculator.AttackType attackType = toCalculatorAttackType(option.attackCategory);
						if (attackType == null)
						{
							continue;
						}

						int attackBonus = getMeleeAttackBonus(option.attackCategory);
						int styleAttackBonus = getStyleAttackBonus(option.styleType);
						int styleStrengthBonus = getStyleStrengthBonus(option.styleType);

						DpsCalculator.DpsResult result = DpsCalculator.calculateMeleeDps(
							attackLevel, strengthLevel, attackBonus, currentStrengthBonus,
							currentWeaponSpeed, attackType, styleAttackBonus, styleStrengthBonus, currentNpc,
							config.meleePrayer(), config.potionBoost(), config.onSlayerTask(),
							false, 1.0
						);

						DpsCalculator.DpsResult prev = bestMelee.get(attackType);
						if (prev == null || result.getDps() > prev.getDps())
						{
							bestMelee.put(attackType, result);
						}
					}
					else if (option.attackCategory == AttackCategory.RANGED)
					{
						RangedStyle style = option.rangedStyle != null ? option.rangedStyle : RangedStyle.ACCURATE;
						rangedStyles.add(style);
					}
				}

				DpsCalculator.AttackType[] meleeOrder = {
					DpsCalculator.AttackType.STAB,
					DpsCalculator.AttackType.SLASH,
					DpsCalculator.AttackType.CRUSH
				};

				for (DpsCalculator.AttackType meleeType : meleeOrder)
				{
					DpsCalculator.DpsResult result = bestMelee.get(meleeType);
					if (result != null)
					{
						String label;
						switch (meleeType)
						{
							case STAB:
								label = "Stab";
								break;
							case SLASH:
								label = "Slash";
								break;
							case CRUSH:
								label = "Crush";
								break;
							default:
								label = meleeType.name();
						}
						rows.add(new ResultRow(label, result));
					}
				}

				RangedStyle[] rangedOrder = {
					RangedStyle.ACCURATE,
					RangedStyle.RAPID,
					RangedStyle.LONGRANGE
				};

				for (RangedStyle style : rangedOrder)
				{
					if (!rangedStyles.contains(style))
					{
						continue;
					}

					int speed = style == RangedStyle.RAPID ? Math.max(1, currentWeaponSpeed - 1) : currentWeaponSpeed;
					DpsCalculator.DpsResult rangedResult = DpsCalculator.calculateRangedDps(
						rangedLevel, currentRangedAttack, currentRangedStr,
						speed, currentNpc,
						config.rangedPrayer(), config.potionBoost(), config.onSlayerTask(),
						false, 1.0
					);
					rows.add(new ResultRow(style.getLabel(), rangedResult));
				}

				if (shouldShowMagic())
				{
					DpsCalculator.DpsResult magicResult = DpsCalculator.calculateMagicDps(
						magicLevel, currentMagicAttack, 30, currentMagicDamageBonus, 5, currentNpc,
						config.magicPrayer(), config.potionBoost(), config.onSlayerTask(),
						false
					);
					rows.add(new ResultRow("Casting", magicResult));
				}
			}

			if (!usedCombatOptions || rows.isEmpty())
			{
				switch (currentCombatType)
				{
					case RANGED:
						DpsCalculator.DpsResult accurateResult = DpsCalculator.calculateRangedDps(
							rangedLevel, currentRangedAttack, currentRangedStr,
							currentWeaponSpeed, currentNpc,
							config.rangedPrayer(), config.potionBoost(), config.onSlayerTask(),
							false, 1.0
						);
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
						rows.add(new ResultRow("Accurate", accurateResult));
						rows.add(new ResultRow("Rapid", rapidResult));
						rows.add(new ResultRow("Longrange", longrangeResult));
						break;
					case MAGIC:
						if (shouldShowMagic())
						{
							DpsCalculator.DpsResult magicResult = DpsCalculator.calculateMagicDps(
								magicLevel, currentMagicAttack, 30, currentMagicDamageBonus, 5, currentNpc,
								config.magicPrayer(), config.potionBoost(), config.onSlayerTask(),
								false
							);
							rows.add(new ResultRow("Casting", magicResult));
						}
						break;
					default:
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
						rows.add(new ResultRow("Stab", stabResult));
						rows.add(new ResultRow("Slash", slashResult));
						rows.add(new ResultRow("Crush", crushResult));
						break;
				}
			}

			for (ResultRow row : rows)
			{
				if (row.result.getDps() > bestDps)
				{
					bestDps = row.result.getDps();
					bestStyle = row.label;
				}
			}

			for (ResultRow row : rows)
			{
				addDpsRow(resultsContent, row.label, row.result, row.label.equals(bestStyle));
			}

			// Add time to kill for best style
			if (!rows.isEmpty())
			{
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
			else
			{
				JLabel noStyles = new JLabel("No combat styles found");
				noStyles.setFont(FontManager.getRunescapeSmallFont());
				noStyles.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				resultsContent.add(noStyles);
			}
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

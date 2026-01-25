/*
 * Copyright (c) 2024
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.dpscalculator;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "DPS Calculator",
	description = "Calculate DPS against NPCs with your current gear and stats",
	tags = {"dps", "calculator", "gear", "combat", "damage"}
)
public class DpsCalculatorPlugin extends Plugin
{
	private static final String CALC_DPS = "Calculate DPS";

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private DpsCalculatorConfig config;

	@Inject
	private NpcStatsManager npcStatsManager;

	@Getter
	private DpsCalculatorPanel panel;

	private NavigationButton navButton;

	@Provides
	DpsCalculatorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DpsCalculatorConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(DpsCalculatorPanel.class);

		// Create a simple sword icon
		final BufferedImage icon = createIcon();

		navButton = NavigationButton.builder()
			.tooltip("DPS Calculator")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		log.info("DPS Calculator started!");
	}

	/**
	 * Create a simple icon for the sidebar
	 */
	private BufferedImage createIcon()
	{
		BufferedImage icon = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = icon.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

		// Draw a sword shape (simple DPS calculator icon)
		g.setColor(new java.awt.Color(200, 200, 200));

		// Blade
		int[] xPoints = {12, 14, 14, 12, 10, 10};
		int[] yPoints = {2, 4, 16, 18, 16, 4};
		g.fillPolygon(xPoints, yPoints, 6);

		// Hilt
		g.setColor(new java.awt.Color(139, 69, 19)); // Brown
		g.fillRect(8, 16, 8, 3);

		// Handle
		g.setColor(new java.awt.Color(100, 50, 10));
		g.fillRect(10, 19, 4, 4);

		g.dispose();
		return icon;
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		log.info("DPS Calculator stopped!");
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		final MenuEntry menuEntry = event.getMenuEntry();
		final NPC npc = menuEntry.getNpc();

		if (npc == null)
		{
			return;
		}

		// Only add "Calculate DPS" option to NPCs that have an "Attack" option
		// NPC_SECOND_OPTION is typically the "Attack" action for attackable NPCs
		if (menuEntry.getType() == MenuAction.NPC_SECOND_OPTION && 
			"Attack".equals(menuEntry.getOption()))
		{
			client.createMenuEntry(-1)
				.setOption(CALC_DPS)
				.setTarget(event.getTarget())
				.setWorldViewId(menuEntry.getWorldViewId())
				.setIdentifier(event.getIdentifier())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> onCalculateDps(npc));
		}
	}

	private void onCalculateDps(NPC npc)
	{
		if (npc == null || npc.getName() == null)
		{
			return;
		}

		final String npcName = Text.removeTags(npc.getName());
		final int npcId = npc.getId();
		final int combatLevel = npc.getCombatLevel();

		log.debug("Calculate DPS for NPC: {} (ID: {}, Combat: {})", npcName, npcId, combatLevel);

		// Look up NPC stats
		NpcStats npcStats = npcStatsManager.getNpcStats(npcId, npcName);

		// Open panel and set the NPC
		SwingUtilities.invokeLater(() ->
		{
			clientToolbar.openPanel(navButton);
			panel.setTargetNpc(npcName, npcId, combatLevel, npcStats);
			panel.refreshPlayerStats();
		});
	}

	/**
	 * Refresh player stats - can be called from config or manually
	 */
	public void refreshPlayerStats()
	{
		if (panel != null)
		{
			SwingUtilities.invokeLater(() -> panel.refreshPlayerStats());
		}
	}
}

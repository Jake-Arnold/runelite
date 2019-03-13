package net.runelite.client.plugins.remotebankcontents;

import java.util.LinkedHashMap;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.ItemManager;

public class RemoteBankContentsProcess
{
	private static final int INVENTORY_ITEM_WIDGETID = WidgetInfo.INVENTORY.getPackedId();
	private LinkedHashMap<Integer, Integer> items = new LinkedHashMap<>();

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	private RemoteBankContentsConfig config;

	@Inject
	RemoteBankContentsProcess(RemoteBankContentsConfig config, Client client)
	{
		this.config = config;
		this.client = client;
	}

	void populateBankItemMap()
	{
		ItemContainer bankInventory = client.getItemContainer(InventoryID.BANK);

		if (bankInventory == null)
		{
			return;
		}

		items.clear();

		for (Item s : bankInventory.getItems())
		{
			items.put(s.getId(), s.getQuantity());
		}

	}

	public String getName(int id)
	{
		return itemManager.getItemComposition(id).getName();
	}


	public int getQuantity(int id)
	{
		return items.get(id) != null ? items.get(id) : 0;
	}


	boolean initialised()
	{
		return items.size() > 0;
	}

	void outputExamine(MenuOptionClicked event)
	{

		int id = event.getId();
		final int widgetId = event.getWidgetId();


		if (!event.getMenuOption().equals("Examine"))
		{
			return;
		}

		if (widgetId != WidgetID.BANK_GROUP_ID && widgetId != WidgetID.BANK_INVENTORY_GROUP_ID)
		{

			if (isUltimateIronman())
			{
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.SERVER).runeLiteFormattedMessage("<col" + ChatColorType.HIGHLIGHT + ">" + "UIM BTW.").build());

			}
			else if (initialised())
			{

				final ChatMessageBuilder message = new ChatMessageBuilder();

				/*Refine message based on quantity and if this necessitates a plural.

				if (quantity == 1) {
					message.append("<col" + ChatColorType.HIGHLIGHT + ">" + "You currently have " + quantity + " " + name + " in your bank.");
				} else {

					//Add s to the end. Need to check if the item ends in S and if it does not add S
					//and if the item ends in Y and add "ies". Also need to check exceptions to this rule.
					message.append("<col" + ChatColorType.HIGHLIGHT + ">" + "You currently have " + quantity + " " + name + "s in your bank.");
				}
                */
				int quantity = getQuantity(id);
				String name = getName(id);

				message.append("<col" + ChatColorType.HIGHLIGHT + ">" + "You currently have " + quantity + " " + name + " in your bank.");

				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.EXAMINE_ITEM).runeLiteFormattedMessage(message.build()).build());
			}
			else
			{
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.SERVER).runeLiteFormattedMessage("<col" + ChatColorType.HIGHLIGHT + ">" + "Please open your bank to initialise.").build());

			}


		}
	}


	/*
	TODO
	 * BUG - currently things people with a 3 at character 5 in name are UIM
	 *
	 *
	 */
	private boolean isUltimateIronman()
	{

		char c = 5;
		Widget w = client.getWidget(WidgetInfo.CHATBOX_INPUT);

		return w.getText().charAt(c) == '3';
	}


}




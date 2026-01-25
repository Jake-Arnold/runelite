package net.runelite.client.plugins.flippingutilities;

public enum SortDirection {
	ASCENDING("Ascending"),
	DESCENDING("Descending");

	private final String displayName;

	SortDirection(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}
}

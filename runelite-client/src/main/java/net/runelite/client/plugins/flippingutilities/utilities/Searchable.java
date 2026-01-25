package net.runelite.client.plugins.flippingutilities.utilities;

import java.time.Instant;

public interface Searchable {
    boolean isInInterval(Instant intervalStart);
    String getNameForSearch();
}

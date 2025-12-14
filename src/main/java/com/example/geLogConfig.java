package net.runelite.client.plugins.geLogPlugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("geLogPlugin")
public interface geLogConfig extends Config {
    @ConfigItem(
            keyName = "autoSave1",
            name = "Auto Save Notes",
            description = "Automatically saves Notepad",
            position = 1
    )
    default boolean autoSave1()
    {
        return true;
    }

    @ConfigItem(
            keyName = "clearhistoryOnAccountSwitch",
            name = "Clear GE History session",
            description = "Clears History in panel on logout",
            position = 2
    )
    default boolean clearhistoryOnAccountSwitch()
    {
        return true;
    }
}
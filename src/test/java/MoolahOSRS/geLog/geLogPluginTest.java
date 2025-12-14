package MoolahOSRS.geLog;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class geLogPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(geLogPlugin.class);
		RuneLite.main(args);
	}
}

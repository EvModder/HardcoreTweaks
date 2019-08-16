package Hardcore;

import org.bukkit.ChatColor;
import org.bukkit.Location;

public class Extras{
	public static String locationToString(Location loc, ChatColor coordColor, ChatColor commaColor){
		return new StringBuilder("")
				.append(ChatColor.GRAY).append(loc.getBlockX()).append(ChatColor.DARK_GRAY).append(",")
				.append(ChatColor.GRAY).append(loc.getBlockY()).append(ChatColor.DARK_GRAY).append(",")
				.append(ChatColor.GRAY).append(loc.getBlockZ()).append(ChatColor.DARK_GRAY).append(",")
				.toString();
	}
}
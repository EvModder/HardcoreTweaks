package Hardcore.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import Hardcore.HCTweaks;
import java.util.Arrays;
import java.util.List;
import net.evmodder.EvLib.bukkit.EvCommand;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.TextUtils;

public class CommandDeathlog extends EvCommand{
	public CommandDeathlog(HCTweaks pl){super(pl);}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){return null;}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		if(args.length == 1 && args[0].toLowerCase().replace("@a", "all").contains("all")){
			String deathLog = ChatColor.stripColor(FileIO.loadFile(HCTweaks.DEATH_LOG_FILENAME, ""));
			sender.sendMessage(TextUtils.translateAlternateColorCodes('&', "&8&m     &7 Recent Deaths &8&m     "));
			sender.sendMessage(deathLog);
		}
		else{
			String[] deathLog = FileIO.loadFile(HCTweaks.DEATH_LOG_FILENAME, "").split("\n");
			String log10 = String.join("\n"+ChatColor.RESET,
					Arrays.copyOfRange(deathLog, Math.max(deathLog.length-10, 0), deathLog.length));
			sender.sendMessage(TextUtils.translateAlternateColorCodes('&', "&8&m     &7 Recent Deaths &8&m     "));
			sender.sendMessage(log10);
		}
		return true;
	}
}
package Hardcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import Hardcore.HCTweaks;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TextUtils;

public class CommandDeathlog extends EvCommand{
	public CommandDeathlog(HCTweaks pl){super(pl);}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){return null;}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		if(args.length == 1 && args[0].toLowerCase().replace("@a", "all").contains("all")){
			String deathLog = FileIO.loadFile("death-log.txt", "");
			sender.sendMessage(TextUtils.translateAlternateColorCodes('&', "&8&m     &7 Recent Deaths &8&m     "));
			sender.sendMessage(deathLog);
		}
		else{
			String[] deathLog = FileIO.loadFile("death-log.txt", "").split("\n");
			String log10 = StringUtils.join(
					Arrays.copyOfRange(deathLog, Math.max(deathLog.length-10, 0), deathLog.length), '\n');
			sender.sendMessage(TextUtils.translateAlternateColorCodes('&', "&8&m     &7 Recent Deaths &8&m     "));
			sender.sendMessage(log10);
		}
		return true;
	}
}
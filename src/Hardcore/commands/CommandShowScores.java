package Hardcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import Hardcore.HCTweaks;
import Hardcore.ScoreboardManager;
import java.util.List;
import net.evmodder.EvLib.bukkit.EvCommand;

public class CommandShowScores extends EvCommand{
	final HCTweaks pl;
	public CommandShowScores(HCTweaks plugin, boolean enabled){
		super(plugin, enabled);
		pl = plugin;
	}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){return null;}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		ScoreboardManager.showOnSidebar("levels", 30);
		return true;
	}
}
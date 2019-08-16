package Hardcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import Hardcore.HCTweaks;
import java.util.List;
import net.evmodder.EvLib.EvCommand;

public class CommandShowScores extends EvCommand{
	final HCTweaks pl;
	public CommandShowScores(HCTweaks plugin){
		super(plugin);
		pl = plugin;
	}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){return null;}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		pl.getServer().getScoreboardManager().getMainScoreboard()
			.getObjective("levels").setDisplaySlot(DisplaySlot.SIDEBAR);
		new BukkitRunnable(){@Override public void run(){
			pl.getServer().getScoreboardManager().getMainScoreboard().clearSlot(DisplaySlot.SIDEBAR);
		}}.runTaskLater(pl, 20*30);
		return true;
	}
}
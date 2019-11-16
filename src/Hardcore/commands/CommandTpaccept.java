package Hardcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import Hardcore.TeleportManager;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.EvPlugin;

public class CommandTpaccept extends EvCommand{
	final EvPlugin pl;
	final TeleportManager tpMan;
	public CommandTpaccept(EvPlugin pl, TeleportManager tpMan){super(pl); this.pl = pl; this.tpMan = tpMan;}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
		if(s instanceof Player == false || args.length == 0) return null;
		args[0] = args[0].toLowerCase();
		ArrayList<String> tabCompletes = new ArrayList<String>();
		List<String> pendingTps = tpMan.get_pending_tps(((Player)s).getUniqueId());
		if(pendingTps.isEmpty()){
			for(Player p : pl.getServer().getOnlinePlayers())
				if(p.getName().toLowerCase().startsWith(args[0])) tabCompletes.add(p.getName());
		}
		else{
			for(String pName : pendingTps)
				if(pName.toLowerCase().startsWith(args[0])) tabCompletes.add(pName);
		}
		return tabCompletes;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		if(sender instanceof Player == false){
			sender.sendMessage(ChatColor.RED+"This command can only be run by in-game players!");
			return true;
		}
		if(args.length == 0){
			sender.sendMessage(ChatColor.RED+"Please specify whose teleport you are accepting");
			sender.sendMessage(ChatColor.GRAY+"Options: ["+
					String.join(", ", tpMan.get_pending_tps(((Player)sender).getUniqueId()))+"]");
			return false;
		}
		@SuppressWarnings("deprecation")
		Player from = pl.getServer().getPlayer(args[0]);
		if(from == null){
			sender.sendMessage(ChatColor.RED+"Could not find online player: "+args[0]);
			return false;
		}
		tpMan.acceptTeleport((Player)sender, from);
		return true;
	}
}
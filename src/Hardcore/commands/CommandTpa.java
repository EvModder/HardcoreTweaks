package Hardcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import Hardcore.TeleportManager;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.EvPlugin;

public class CommandTpa extends EvCommand{
	final EvPlugin pl;
	final TeleportManager tpMan;
	public CommandTpa(EvPlugin pl, TeleportManager tpMan){super(pl); this.pl = pl; this.tpMan = tpMan;}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
		if(s instanceof Player == false || args.length == 0) return null;
		args[0] = args[0].toLowerCase();
		ArrayList<String> tabCompletes = new ArrayList<String>();
		for(Player p : pl.getServer().getOnlinePlayers()){
			if(tabCompletes.size() > 20) break;//Checking isValidReceiver might be expensive...
			if(p.getName().toLowerCase().startsWith(args[0]) &&
					tpMan.isValidReceiver(/*from=*/(Player)s, /*target=*/p, /*tellFromWhyNot=*/false, /*tellReceiverWhyNot=*/false))
				tabCompletes.add(p.getName());
		}
		return tabCompletes;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		if(tpMan.isValidRequester(sender, /*tellWhyNot=*/true) == false) return true;

		if(args.length == 0){
			sender.sendMessage(ChatColor.RED+"Please specify who you wish to teleport to");
			return false;
		}
		Player target = pl.getServer().getPlayer(args[0]);
		if(target == null){
			@SuppressWarnings("deprecation")
			OfflinePlayer offP = pl.getServer().getOfflinePlayer(args[0]);
			if(offP != null && offP.hasPlayedBefore()){
				sender.sendMessage(ChatColor.RED+offP.getName()+" needs to be online");
				return true;
			}
			sender.sendMessage(ChatColor.RED+"Could not find player: "+args[0]);
			return false;
		}

		if(tpMan.isValidReceiver((Player)sender, target, /*tellFromWhyNot=*/true, /*tellReceiverWhyNot=*/false) == false) return true;
		tpMan.addPendingTpa((Player)sender, target);
		return true;
	}
}
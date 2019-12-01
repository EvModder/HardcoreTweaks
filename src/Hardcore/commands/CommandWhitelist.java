package Hardcore.commands;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import Hardcore.HCTweaks;
import Hardcore.SpectatorManager;
import Hardcore.SpectatorManager.WatchMode;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.evmodder.EvLib.EvCommand;

public class CommandWhitelist extends EvCommand{
	final HCTweaks pl;

	public CommandWhitelist(HCTweaks plugin){
		super(plugin);
		pl = plugin;
	}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String label, String[] args){
		label = label.toLowerCase();
		if(args.length == 1){
			if("add".startsWith(args[0])) return Arrays.asList("add");
			if("remove".startsWith(args[0])) return Arrays.asList("remove");
			return null;
		}
		else if(args.length == 2){
			return pl.getServer().getOnlinePlayers()
					.stream().map(p -> p.getName())
					.filter(name -> name.toLowerCase().startsWith(args[1]))
					.collect(Collectors.toList());
		}
		return null;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		if(sender instanceof Player == false){
			if(args.length != 1){
				sender.sendMessage(ChatColor.RED+"Console usage: /whitelist <name>");
			}
			else{
				@SuppressWarnings("deprecation")
				Player target = pl.getServer().getPlayer(args[0]);
				if(target == null) sender.sendMessage(ChatColor.RED+"Could not find player \""+args[0]+"\"");
				else CommandSpectate.displayWhitelist(sender, target);
			}
			return true;
		}
		Player player = (Player) sender;
		if(SpectatorManager.getSpectateMode(player) == WatchMode.BLACKLIST){
			sender.sendMessage(ChatColor.GOLD+"Warning: "+ChatColor.GRAY+"your current mode is "+ChatColor.RED+"BLACKLIST"
					+ChatColor.GRAY+".\nTo change to "+ChatColor.RED+"WHITELIST"+ChatColor.GRAY+" mode, run "
					+ChatColor.GREEN+"/spectate mode whitelist");
		}
		if(args.length == 0){
			CommandSpectate.displayWhitelist(player, player);
			return true;
		}
		if(args.length > 2){
			sender.sendMessage(ChatColor.RED+"Too many arguments");
			return true;
		}
		if(args.length == 1){
			@SuppressWarnings("deprecation")
			OfflinePlayer spectator = pl.getServer().getOfflinePlayer(args[0]);
			if(spectator == null || spectator.getUniqueId().equals(player.getUniqueId())){
				sender.sendMessage(ChatColor.RED+"Could not find player \""+args[0]+"\"");
			}
			else{
				if(player.addScoreboardTag("spectator_whitelist_"+spectator.getUniqueId())){
					sender.sendMessage(ChatColor.AQUA+spectator.getName()+ChatColor.GRAY+" has been added to your whitelist");
					player.removeScoreboardTag("spectator_blacklist_"+spectator.getUniqueId());
				}
				else{
					player.removeScoreboardTag("spectator_whitelist_"+spectator.getUniqueId());
					sender.sendMessage(ChatColor.AQUA+spectator.getName()+ChatColor.GRAY+" has been removed from your whitelist");
				}
			}
			return true;
		}
		args[0] = args[0].toLowerCase();
		if(!args[0].equals("add") && !args[0].equals("remove")){
			sender.sendMessage(ChatColor.RED+"Unknown operation \""+args[0]+"\"");
			return true;
		}
		
		@SuppressWarnings("deprecation")
		OfflinePlayer spectator = pl.getServer().getOfflinePlayer(args[1]);
		if(spectator == null || spectator.getUniqueId().equals(player.getUniqueId())){
			sender.sendMessage(ChatColor.RED+"Could not find player \""+args[1]+"\"");
			return true;
		}
		if(args[0].equals("add")){
			player.addScoreboardTag("spectator_whitelist_"+spectator.getUniqueId());
			player.removeScoreboardTag("spectator_blacklist_"+spectator.getUniqueId());
			sender.sendMessage(ChatColor.AQUA+spectator.getName()+ChatColor.GRAY+" has been added to your whitelist");
		}
		else/* if(args[0].equals("remove"))*/{
			player.removeScoreboardTag("spectator_whitelist_"+spectator.getUniqueId());
			sender.sendMessage(ChatColor.AQUA+spectator.getName()+ChatColor.GRAY+" has been removed from your whitelist");
		}
		return true;
	}
}
package Hardcore.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import Hardcore.HCTweaks;

public class TEMP_CmdPing implements Listener {
	final HCTweaks pl;

	public TEMP_CmdPing(HCTweaks plugin){
		pl = plugin;
	}

	@EventHandler
	public void onPreCommand(PlayerCommandPreprocessEvent evt){
		if(evt.getMessage().charAt(0) != '/' || evt.isCancelled()) return;
		String message = evt.getMessage().trim();
		String command = message.toLowerCase().trim();
		int space = command.indexOf(' ');
		command = (space > 0 ? command.substring(1, space) : command.substring(1));
		Player player = evt.getPlayer();

		if(command.equals("ping")){
			Player target = space == -1 ? player : pl.getServer().getPlayer(message.substring(space + 1));
			if(target != null){
				player.sendMessage(ChatColor.GOLD+"Ping: "+ChatColor.GREEN+CommandAutoQuit.getPing(target));
			}
			//if(!player.hasPermission("essentials.ping"))
				evt.setCancelled(true);
		}
	}
}
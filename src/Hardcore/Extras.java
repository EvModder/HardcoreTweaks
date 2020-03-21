package Hardcore;

import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

class Extras implements Listener{
	Extras(){
		HCTweaks pl = HCTweaks.getPlugin();
		pl.getServer().getPluginManager().registerEvents(this, pl);
	}

	@EventHandler
	public void onPreCommand(PlayerCommandPreprocessEvent evt){
		if(evt.getMessage().charAt(0) != '/' || evt.isCancelled()) return;
		String message = evt.getMessage().toLowerCase();
		String command = message.split(" ")[0];

		if(command.contains("suicide") && !evt.getPlayer().isOp() && evt.getPlayer().getGameMode() == GameMode.SPECTATOR){
			evt.setCancelled(true);
		}
	}
}

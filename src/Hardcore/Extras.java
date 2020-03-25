package Hardcore;

import org.bukkit.GameMode;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import net.evmodder.HorseOwners.HorseLibrary;

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
	@EventHandler
	public void onChunkLoad(ChunkLoadEvent evt){
		if(evt.isNewChunk() || evt.isAsynchronous()) return;
		for(Entity e : evt.getChunk().getEntities()){
			if(e instanceof AbstractHorse){
				AbstractHorse horse = (AbstractHorse)e;
				double speed = HorseLibrary.getNormalSpeed(horse), jump = HorseLibrary.getNormalJump(horse), health = HorseLibrary.getNormalMaxHealth(horse);
				if(speed > 16 || jump > 6 || health > 35){
					horse.remove();
					HCTweaks.getPlugin().getLogger().warning("Deleted illegit horse: s,j,h="+speed+","+jump+","+health);
				}
			}
		}
	}
}

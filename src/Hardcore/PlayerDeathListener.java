package Hardcore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import net.evmodder.EvLib.FileIO;

public class PlayerDeathListener implements Listener{
	final HCTweaks pl;
	public PlayerDeathListener(HCTweaks plugin){ pl = plugin; }

	@EventHandler
	public void onDeath(PlayerDeathEvent evt){
		final UUID uuid = evt.getEntity().getUniqueId();
		final String name = evt.getEntity().getName();
		evt.getEntity().saveData();
		evt.getEntity().loadData();
		evt.getEntity().getScoreboard().resetScores(name);
		if(name.length() > 10) evt.getEntity().getScoreboard().resetScores(name.substring(0, 10));
		evt.getEntity().addScoreboardTag("dead");
		pl.getLogger().warning("Death of "+name+": "+evt.getDeathMessage());

		String deathLog = FileIO.loadFile("death-log.txt", "");
		if(!deathLog.isEmpty()) deathLog += "\n";
		deathLog += evt.getDeathMessage();
		FileIO.saveFile("death-log.txt", deathLog);

		new BukkitRunnable(){@Override public void run(){
			String dateStr = new SimpleDateFormat("yyy-MM-dd").format(new Date());
			String deathDir = "./plugins/EvFolder/deaths";
			if(!new File(deathDir).exists()) new File(deathDir).mkdir();
			deathDir += "/"+/*uuid*/name;
			if(!new File(deathDir).exists()) new File(deathDir).mkdir();
			deathDir += "/"+dateStr;
			if(new File(deathDir).exists()){
				int i = 1;
				while(new File(deathDir+"."+i).exists()) ++i;
				deathDir += "."+i;
			}
			new File(deathDir).mkdir();
			pl.getLogger().warning("Copying playerdata for "+name+"...");
			if(!pl.copyPlayerdata(uuid, deathDir)) pl.getLogger().severe("Copy faied");
		}}.runTaskLater(pl, 20 * 2);

		// Kick after 2 minutes (to prevent item despawn) if they still haven't respawned
		new BukkitRunnable(){@Override public void run(){
			if(evt.getEntity().isDead())
				evt.getEntity().kickPlayer(ChatColor.RED + "Died in hardcore:"
									+ChatColor.RESET+"\n"+evt.getDeathMessage());
		}}.runTaskLater(pl, 20 * 120);
	}
}
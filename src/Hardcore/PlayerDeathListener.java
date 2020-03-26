package Hardcore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import net.evmodder.EvLib.FileIO;

public class PlayerDeathListener implements Listener{
	final String SPEC_PREFIX = /*ChatColor.translateAlternateColorCodes('&', */"&9[&7Dead&9]&f·"/*)*/;
	final HCTweaks pl;
	public PlayerDeathListener(HCTweaks plugin){ pl = plugin; }
	final long QD_HRS = 5;

	@EventHandler
	public void onDeath(PlayerDeathEvent evt){
		final UUID uuid = evt.getEntity().getUniqueId();
		final String name = evt.getEntity().getName();
		//TODO: "PLAY_ONE_MINUTE" actually seems to track ticks lived??
		//final boolean quickDeath = evt.getEntity().getStatistic(Statistic.PLAY_ONE_MINUTE)/60 < QD_HRS;//Less than 5 hours alive
		final boolean quickDeath = evt.getEntity().getStatistic(Statistic.TIME_SINCE_DEATH)/20/60/60 < QD_HRS;// Equivalent to above line
		evt.getEntity().saveData();
		evt.getEntity().loadData();
		ScoreboardManager.resetScores(evt.getEntity());
		evt.getEntity().addScoreboardTag("dead");
		//evt.getEntity().setDisplayName(SPEC_PREFIX+" "+evt.getEntity().getDisplayName());
		pl.runCommand("essentials:nick "+name+" "+SPEC_PREFIX+evt.getEntity().getDisplayName());
		pl.getLogger().warning("Death of "+name+": "+evt.getDeathMessage());
		pl.getLogger().info("Was quick-death: "+quickDeath);

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
			{
				if(!quickDeath) new File(deathDir+"/quick-deaths.txt").delete();
				else{
					int quickDeaths = Integer.parseInt(FileIO.loadFile("deaths/"+name+"/quick-deaths.txt", "0")) + 1;
					FileIO.saveFile("deaths/"+name+"/quick-deaths.txt", ""+quickDeaths);
					long respawnPenalty = (1 << (quickDeaths-1)) * (60*60*6/*6 hours*/);
					evt.getEntity().addScoreboardTag("respawn_penalty="+respawnPenalty);
					pl.getLogger().info("Quick-deaths for "+name+": "+quickDeaths);
				}
			}
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

		// Kick after 2 minutes (to prevent item despawn) if they still haven't pressed respawn
		new BukkitRunnable(){@Override public void run(){
			Player deadPlayer = pl.getServer().getPlayer(uuid);
			if(deadPlayer != null && deadPlayer.isDead())
				deadPlayer.kickPlayer(ChatColor.RED + "Died in hardcore:"+ChatColor.RESET+"\n"+evt.getDeathMessage());
		}}.runTaskLater(pl, 20 * 120);
	}
}
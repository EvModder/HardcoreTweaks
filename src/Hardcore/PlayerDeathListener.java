package Hardcore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TextUtils;

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
		long millis_alive = evt.getEntity().getStatistic(Statistic.TIME_SINCE_DEATH)*50;
		final boolean quickDeath = millis_alive/1000/60/60 < QD_HRS;// Equivalent to line 2 above
//		pl.getLogger().warning("Death of "+name+": \""+evt.getDeathMessage()+'"');

		evt.getEntity().saveData();
		evt.getEntity().loadData();

		// Initialize dead-person things
		ScoreboardManager.resetScores(evt.getEntity());
		evt.getEntity().addScoreboardTag("dead");
		pl.getServer().getScoreboardManager().getMainScoreboard().getTeam("Dead").addEntry(name);
		//evt.getEntity().setDisplayName(SPEC_PREFIX+" "+evt.getEntity().getDisplayName());//TODO: dead prefix in chat
		//pl.runCommand("essentials:nick "+name+" "+SPEC_PREFIX+evt.getEntity().getDisplayName());

		// Write to death-log.txt
		String deathLog = FileIO.loadFile("death-log.txt", "");
		if(!deathLog.isEmpty()) deathLog += "\n";
		deathLog += evt.getDeathMessage();
		FileIO.saveFile("death-log.txt", deathLog);

		// Save playerdata files & update quick-deaths.txt
		new BukkitRunnable(){@Override public void run(){
			String dateStr = new SimpleDateFormat("yyy-MM-dd").format(new Date());
			String deathDir = "./plugins/EvFolder/deaths";
			if(!new File(deathDir).exists()) new File(deathDir).mkdir();
			deathDir += "/"+/*uuid*/name;
			if(!new File(deathDir).exists()) new File(deathDir).mkdir();

			// Update quick-deaths:
			int quickDeaths = 1;
			if(!quickDeath) new File(deathDir+"/quick-deaths.txt").delete();
			else{
				quickDeaths += Integer.parseInt(FileIO.loadFile("deaths/"+name+"/quick-deaths.txt", "0"));
				FileIO.saveFile("deaths/"+name+"/quick-deaths.txt", ""+quickDeaths);
				long respawnPenalty = (1 << (quickDeaths-1)) * (60*60*6/*6 hours*/);
				evt.getEntity().addScoreboardTag("respawn_penalty_"+respawnPenalty);
//				pl.getLogger().info("Quick-deaths for "+name+": "+quickDeaths);
			}
			pl.getLogger().info("Was quick-death: "+quickDeath
					+" ("+TextUtils.formatTime(millis_alive, false, "§6", "§c", " ")
					+ChatColor.RESET+(quickDeath ? " | §6streak=§c"+quickDeaths : "")+"§r)");
			// Update death-stats
			HashMap<EntityType, Integer> killedByStats = new HashMap<>();
			for(EntityType entity : EntityType.values()){
				try{
					int killedByEntity = evt.getEntity().getStatistic(Statistic.ENTITY_KILLED_BY, entity);
					if(killedByEntity > 0) killedByStats.put(entity, killedByEntity);
				}
				catch(IllegalArgumentException ex){};
			}
			if(!killedByStats.isEmpty()){
				String text = killedByStats.entrySet().stream().map(entry -> entry.getKey()+","+entry.getValue())
						.sorted().collect(Collectors.joining("\n"));
				FileIO.saveFile("deaths/"+name+"/death-stats.txt", text);
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

		/*
		// Kick after 2 minutes (to prevent item despawn) if they still haven't pressed respawn
		// NOTE: not necessary if gamerule spectatorsGenerateChunks if false (i think)
		new BukkitRunnable(){@Override public void run(){
			Player deadPlayer = pl.getServer().getPlayer(uuid);
			if(deadPlayer != null && deadPlayer.isDead())
				deadPlayer.kickPlayer(ChatColor.RED + "Died in hardcore:"+ChatColor.RESET+"\n"+evt.getDeathMessage());
		}}.runTaskLater(pl, 20 * 120);*/
	}
}
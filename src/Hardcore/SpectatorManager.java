package Hardcore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.EndGateway;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.extras.ActionBarUtils;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.util.Pair;

public class SpectatorManager implements Listener{
	final HCTweaks pl;
	public enum WatchMode {BLACKLIST, WHITELIST};
	static WatchMode DEFAULT_MODE;
	final int BELOW_SEA_LVL_BOUNCE_DIST_SQ = 10*10, BOUNCE_DIST_SQ = 20*20, TP_DIST_SQ = 50*50, SEND_NOTICE_DIST_SQ = 50*50;
	final double SPEC_SEE_THRU_RADIUS = 2, SPEC_SEE_THRU_CLEANUP_RADIUS = 7;//, SPEC_INV_SYNC_RATE = 2;
	final float FLY_SPEED = 0.08f;
	final long SECONDS_UNTIL_RESPAWN;
	static HashSet<UUID> spectators;
	final Location SPECTATOR_BOX;
	static boolean CLOSEST_ACROSS_DIMENSIONS = true, ANTI_XRAY_SEE_THRU = false;
	final boolean ALLOW_SPECTATORS, SYNC_SPECTATOR_INVS;

	public SpectatorManager(HCTweaks plugin){
		pl = plugin;
		ALLOW_SPECTATORS = pl.getConfig().getBoolean("enable-spectating", true);
		SYNC_SPECTATOR_INVS = pl.getConfig().getBoolean("spectators-sync-inv", true);
		SECONDS_UNTIL_RESPAWN = pl.getConfig().getInt("respawn-wait", 24)*60*60;
		if(pl.getServer().getScoreboardManager().getMainScoreboard().getTeam("Spectators") == null){
			pl.getServer().getScoreboardManager().getMainScoreboard().registerNewTeam("Spectators");
		}
		if(pl.getServer().getScoreboardManager().getMainScoreboard().getTeam("Dead") == null){
			pl.getServer().getScoreboardManager().getMainScoreboard().registerNewTeam("Dead");
		}
		DEFAULT_MODE = pl.getConfig().getString("spectator-mode", "blacklist").equals("blacklist") ?
				WatchMode.BLACKLIST : WatchMode.WHITELIST;
		spectators = new HashSet<UUID>();

		SPECTATOR_BOX = TextUtils.getLocationFromString(pl.getConfig().getString("send-to-loc-when-nobody-to-spectate", "world,-50,-50,-50"));
		List<BlockFace> dirs = Arrays.asList(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN);
		for(Block b : EvUtils.getConnectedBlocks(SPECTATOR_BOX.getBlock(), (b)->true, dirs, /*MAX_SIZE=*/1064/*sphere of radius 5*/)){
			if(b.getType() == Material.END_GATEWAY){
				EndGateway gatewayState = (EndGateway)b.getState();
				gatewayState.setAge(-2147483648);
				gatewayState.update(true);
			}
		}
		loopActive = false;
		runSpectatorLoop();
	}

	public static boolean isSpectator(Player player){
		return (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) && player.getScoreboardTags().contains("dead");
	}

	static boolean isSpectatorFavorYes(Player player){
		return player.getGameMode() == GameMode.SPECTATOR || player.isDead() || player.getScoreboardTags().contains("dead")
				|| (spectators != null && spectators.contains(player.getUniqueId()));
	}

	public static boolean canSpectate(UUID spectator, Player target){
		// TODO: TEMP, remove after archery event
		if(target.getLocation().getX() < -29990000 && target.getLocation().getZ() < -29990000) return false;
		if(spectator.equals(target.getUniqueId())) return false;
		String bl_tag = "spectator_blacklist_"+spectator;
		String wl_tag = "spectator_whitelist_"+spectator;
		boolean ans = !target.getScoreboardTags().contains(bl_tag) &&
				(getSpectateMode(target) != WatchMode.WHITELIST
				|| target.getScoreboardTags().contains(wl_tag));
		//org.bukkit.Bukkit.getLogger().info(org.bukkit.Bukkit.getOfflinePlayer(spectator).getName()
		//		+ " able to spectate "+target.getName()+": "+ans);
		return ans;
	}
	public static WatchMode getSpectateMode(Player player){
		return player.getScoreboardTags().contains("whitelist_mode") ? WatchMode.WHITELIST
				: player.getScoreboardTags().contains("blacklist_mode") ? WatchMode.BLACKLIST
				: DEFAULT_MODE;
	}
	public static void setSpectateMode(Player player, WatchMode mode){
		if(mode == WatchMode.WHITELIST){
			player.addScoreboardTag("whitelist_mode");
			player.removeScoreboardTag("blacklist_mode");
		}
		else if(mode == WatchMode.BLACKLIST){
			player.addScoreboardTag("blacklist_mode");
			player.removeScoreboardTag("whitelist_mode");
		}
		else org.bukkit.Bukkit.getLogger().severe("Unknown spectate mode: "+mode);
	}
	public static boolean isDefaultSpectateMode(Player player){
		return !player.getScoreboardTags().contains("whitelist_mode") && 
				!player.getScoreboardTags().contains("blacklist_mode");
	}

	static Player getClosestGm0WithPerms(Location loc, Player spec){
		double closestDistGm0 = Double.MAX_VALUE;
		Player closestPlayer = null;
		if(!CLOSEST_ACROSS_DIMENSIONS) for(Player p : loc.getWorld().getPlayers()){
			double dist;
			if(p.getGameMode() == GameMode.SURVIVAL && (spec == null || canSpectate(spec.getUniqueId(), p)) &&
					(dist=p.getLocation().distanceSquared(loc)) < closestDistGm0){
				closestDistGm0 = dist;
				closestPlayer = p;
			}
		}
		if(closestPlayer == null){
			double dist;
			for(Player p : org.bukkit.Bukkit.getServer().getOnlinePlayers()){
				if(p.getGameMode() == GameMode.SURVIVAL/* && !p.isDead()*/ && (spec == null || canSpectate(spec.getUniqueId(), p))){
					if((dist=EvUtils.crossDimensionalDistanceSquared(loc, p.getLocation())) <= closestDistGm0){
						closestDistGm0 = dist;
						closestPlayer = p;
					}
				}
			}
		}
		return closestPlayer;
	}

	public static String formatTimeUntilRespawn(long secondsLeft, ChatColor numC, ChatColor textC){
		long minutesLeft = secondsLeft / 60, hoursLeft = minutesLeft / 60, daysLeft = hoursLeft / 24;
		long remSeconds = secondsLeft % 60, remMinutes = minutesLeft % 60, remHours = hoursLeft % 24;

		StringBuilder builder = new StringBuilder("").append(textC).append("Respawn in: ").append(numC);
		if(daysLeft > 0) builder.append(daysLeft).append(textC).append('d').append(numC);
		if(hoursLeft > 0){
			if(remHours < 10 && daysLeft > 0) builder.append("0");
			builder.append(remHours).append(textC).append('h').append(numC);
		}
		if(minutesLeft > 0){
			if(remMinutes < 10 && hoursLeft > 0) builder.append("0");
			builder.append(remMinutes).append(textC).append('m').append(numC);
		}
		if(secondsLeft > 0){
			if(remSeconds < 10 && minutesLeft > 0) builder.append("0");
			builder.append(remSeconds).append(textC).append('s');
		}
		else builder.append("now");
		return builder.toString();
	}

	private void sendSpectateBeginNotice(Player spectator, Player target, Location cameFrom){
		//if(target != null) pl.getLogger().info(spectator.getName()+" is now spectating "+target.getName());
		spectator.sendMessage(ChatColor.GRAY+"You are now spectating "+ChatColor.AQUA+target.getDisplayName());
		if(!spectator.hasPermission("hardcore.spectator.bypass.notify")
				&& target != null
				&& !target.getScoreboardTags().contains("mute_spectate_notify")
				&& (cameFrom == null || !target.getWorld().getUID().equals(cameFrom.getWorld().getUID())
					|| target.getLocation().distanceSquared(cameFrom) > 100*100))
			target.sendMessage("§7§l+ §b"+spectator.getDisplayName()+"§7 is now spectating you");
	}
	private void sendSpectateEndNotice(Player spectator, Player target, Location leftTo){
		//if(target != null) pl.getLogger().info(spectator.getName()+" is no longer spectating "+target.getName());
		//specP.sendMessage(ChatColor.GRAY+"You are no longer spectating "+ChatColor.AQUA+target.getDisplayName());
		if(!spectator.hasPermission("hardcore.spectator.bypass.notify")
				&& target != null
				&& !target.getScoreboardTags().contains("mute_spectate_notify")
				&& (leftTo == null || !target.getWorld().getUID().equals(leftTo.getWorld().getUID())
					|| target.getLocation().distanceSquared(leftTo) > 100*100))
			target.sendMessage("§7§l- §b"+spectator.getDisplayName()+"§7 is no longer spectating you");
	}

	private boolean canSeeThroughBlocks(Location eyeLoc){
		Block b = eyeLoc.getBlock();
		if(!b.getType().isOccluding()) return false;
		final BlockFace[] snoopDirs = new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
		int snoopScore = 0;
		for(BlockFace face : snoopDirs) if(b.getRelative(face).getType().isOccluding()) ++snoopScore;
		return snoopScore >= 3;
	}

	private Material seeThruReplacement(Material type){
		switch(type){
			case DIRT:
				return Material.BROWN_STAINED_GLASS;
			case GRASS_BLOCK:
				return Material.GREEN_STAINED_GLASS;
			case STONE:
			case COAL_ORE:
			case IRON_ORE:
			case GOLD_ORE:
			case EMERALD_ORE:
			case DIAMOND_ORE:
			case REDSTONE_ORE:
				return Material.LIGHT_GRAY_STAINED_GLASS;
			case ANDESITE:
			case GRAVEL:
				return Material.GRAY_STAINED_GLASS;
			case GRANITE:
				return Material.ORANGE_STAINED_GLASS;
			case DIORITE:
				return Material.WHITE_STAINED_GLASS;
			//case COAL_ORE:
			//	return Material.BLACK_STAINED_GLASS;
			default:
				return null;
		}
	}
	private boolean makeSeeThru(Player specP, Location eyeLoc){
		final double SPEC_SEE_THRU_RADIUS_SQ = SPEC_SEE_THRU_RADIUS*SPEC_SEE_THRU_RADIUS;
		for(int x=eyeLoc.getBlockX()-(int)(1+SPEC_SEE_THRU_RADIUS); x<=eyeLoc.getBlockX()+SPEC_SEE_THRU_RADIUS+1; ++x)
		for(int y=eyeLoc.getBlockY()-(int)(1+SPEC_SEE_THRU_RADIUS); y<=eyeLoc.getBlockY()+SPEC_SEE_THRU_RADIUS+1; ++y)
		for(int z=eyeLoc.getBlockZ()-(int)(1+SPEC_SEE_THRU_RADIUS); z<=eyeLoc.getBlockZ()+SPEC_SEE_THRU_RADIUS+1; ++z){
			Block block = eyeLoc.getWorld().getBlockAt(x, y, z);
			if(block.getLocation().distanceSquared(eyeLoc) <= SPEC_SEE_THRU_RADIUS_SQ){
				if(!block.getType().isOccluding()) continue;
				Material seeThruType = seeThruReplacement(block.getType());
				if(seeThruType == null) return false;
				specP.sendBlockChange(block.getLocation(), seeThruType.createBlockData());
			}
		}
		return true;
	}
	private void cleanupSeeThru(Player specP, Location eyeLoc, double SEE_THRU_RADIUS){
		final double SPEC_SEE_THRU_RADIUS_SQ = SEE_THRU_RADIUS*SEE_THRU_RADIUS;
		for(int x=eyeLoc.getBlockX()-(int)SPEC_SEE_THRU_CLEANUP_RADIUS; x<=eyeLoc.getBlockX()+SPEC_SEE_THRU_CLEANUP_RADIUS; ++x)
		for(int y=eyeLoc.getBlockY()-(int)SPEC_SEE_THRU_CLEANUP_RADIUS; y<=eyeLoc.getBlockY()+SPEC_SEE_THRU_CLEANUP_RADIUS; ++y)
		for(int z=eyeLoc.getBlockZ()-(int)SPEC_SEE_THRU_CLEANUP_RADIUS; z<=eyeLoc.getBlockZ()+SPEC_SEE_THRU_CLEANUP_RADIUS; ++z){
			Block block = eyeLoc.getWorld().getBlockAt(x, y, z);
			if(block.getLocation().distanceSquared(eyeLoc) > SPEC_SEE_THRU_RADIUS_SQ){
				if(!block.getType().isOccluding()) continue;
				specP.sendBlockChange(block.getLocation(), block.getBlockData());
			}
		}
	}

	boolean loopActive = false;
	void runSpectatorLoop(){
		if(loopActive) return;
		loopActive = true;
		new BukkitRunnable(){@Override public void run(){
			for(Player p : pl.getServer().getOnlinePlayers()) addSpectator(p);
			//p.kickPlayer(ChatColor.RED+"There is nobody online who you can spectate right now");
			Iterator<UUID> it = spectators.iterator();
			while(it.hasNext()){
				UUID uuid = it.next();
				OfflinePlayer p = pl.getServer().getPlayer(uuid);
				if(p == null || !p.isOnline() || !isSpectator(p.getPlayer())) it.remove();
			}
			if(spectators.isEmpty()){
				pl.getLogger().info("No spectators remaining, setting loopAtive=false");
				//HandlerList.unregisterAll(SpectatorListener.this);
				cancel();
				loopActive = false;
			}
			for(UUID uuid : spectators){
				Player specP = pl.getServer().getPlayer(uuid).getPlayer();

				int secondsSinceDeath = specP.getStatistic(Statistic.TIME_SINCE_DEATH) / 20;
				long frequentDeathPenalty = HCTweaks.getFrequentDeathRespawnPenalty(specP);
				long secondsLeft = SECONDS_UNTIL_RESPAWN + frequentDeathPenalty - secondsSinceDeath;
				final String msg = secondsLeft <= 0
						? ChatColor.GREEN+"You may now respawn with "+ChatColor.AQUA+"/respawn"
						: formatTimeUntilRespawn(secondsLeft, ChatColor.GOLD, ChatColor.GRAY);
				//Note: Doing "/title <Name> actionbar <msg>" will spam console chat
//				pl.runCommand("minecraft:title "+specP.getName()+" actionbar \""+msg+"\"");
				ActionBarUtils.sendToPlayer(msg, specP);

				if(specP.isDead() || specP.isOp()) continue; // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

				if(!specP.hasPermission("hardcore.spectator.bypass.slowness")) specP.setFlySpeed(FLY_SPEED);
				boolean needSeeThruCleanup = specP.removeScoreboardTag("cleanup_see_thru");

				Entity target = specP.getSpectatorTarget();
				if(target != null && target instanceof Player && !isSpectatorFavorYes((Player)target)){
					specP.addScoreboardTag("has_target");
					if(SYNC_SPECTATOR_INVS){
						specP.getInventory().setStorageContents(((Player)target).getInventory().getStorageContents());
						specP.getInventory().setArmorContents(((Player)target).getInventory().getArmorContents());
						specP.getInventory().setExtraContents(((Player)target).getInventory().getExtraContents());
					}
				}
				else{ // No valid target found!
					if(specP.removeScoreboardTag("has_target") && SYNC_SPECTATOR_INVS) specP.getInventory().clear();
					Player newTarget = getClosestGm0WithPerms(specP.getLocation(), specP);
					if(newTarget == null){
						if(!specP.hasPermission("hardcore.spectator.bypass.slowness")) specP.setFlySpeed(FLY_SPEED);
						specP.removePotionEffect(PotionEffectType.BLINDNESS);
						if(specP.getLocation().distanceSquared(SPECTATOR_BOX) > 8D){
							specP.teleport(SPECTATOR_BOX, TeleportCause.CHORUS_FRUIT);//CHORUS_FRUIT is a hack to bypass TPmanager
						}
						SPECTATOR_BOX.getChunk().setForceLoaded(true);
						new BukkitRunnable(){@Override public void run(){
							if((specP.getSpectatorTarget() == null || specP.getSpectatorTarget().getType() != EntityType.ARMOR_STAND)
//									&& specP.getLocation().distanceSquared(SPECTATOR_BOX) > 0.1D
							){
								specP.sendTitle(" ", "There is nobody who you can spectate", 10, 20*60, 20);
								Entity nearestArmorstand = null;
								for(Entity e : specP.getNearbyEntities(10, 10, 10)){
									if(e.getType() == EntityType.ARMOR_STAND){
										if(nearestArmorstand == null || e.getLocation().distanceSquared(SPECTATOR_BOX)
													< nearestArmorstand.getLocation().distanceSquared(SPECTATOR_BOX)) nearestArmorstand = e;
									}
								}
								specP.setSpectatorTarget(nearestArmorstand);
							}
						}}.runTaskLater(pl, 10);
						// Kick specator after 60s if there is nobody online for them to spectate
						/*if(specP.getScoreboardTags().contains("spectating")){
							specP.removeScoreboardTag("spectating");
							if(!specP.hasPermission("hardcore.spectator.bypass.slowness")) specP.setFlySpeed(FLY_SPEED);
							specP.removePotionEffect(PotionEffectType.BLINDNESS);
							specP.teleport(SPECTATOR_BOX, TeleportCause.CHORUS_FRUIT);//CHORUS_FRUIT is a hack to bypass TPmanager
							specP.sendTitle(" ", "There is nobody who you can spectate", 10, 20*60, 20);
							new BukkitRunnable(){@Override public void run(){
								if(!specP.getScoreboardTags().contains("spectating") && isSpectator(specP)){
									specP.kickPlayer(ChatColor.RED+"There is nobody online who you can spectate right now");
								}
							}}.runTaskLater(pl, 20*60);
						}*/
						continue;
					}
					if(!specP.getScoreboardTags().contains("spectating")){
						specP.resetTitle();
						specP.addScoreboardTag("spectating");
						if(!specP.hasPermission("hardcore.spectator.bypass.blindness")){
							specP.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1000000, 0, true));
						}
					}
					if(!specP.hasPermission("hardcore.spectator.bypass.forcedtarget")){
						final UUID newTargetUUID = newTarget.getUniqueId();
						if(specP.teleport(newTarget, TeleportCause.SPECTATE)){
							//specP.setSpectatorTarget(newTarget);
							new BukkitRunnable(){@Override public void run(){
								Player newTarget = pl.getServer().getPlayer(newTargetUUID);
								if(target != null) specP.setSpectatorTarget(newTarget);
							}}.runTaskLater(pl, 15);
						}
					}
					else if(!specP.hasPermission("hardcore.spectator.bypass.maxrange")){
						double distSqToTarget = EvUtils.crossDimensionalDistanceSquared(specP.getLocation(), newTarget.getLocation());
						if(distSqToTarget >= TP_DIST_SQ || !specP.getWorld().getUID().equals(newTarget.getWorld().getUID())){
							//sendSpectateNotice(specP, newTarget, specP.getLocation());//Done by TeleportListener below
							specP.teleport(newTarget, TeleportCause.SPECTATE);
						}
						else if(distSqToTarget >= BOUNCE_DIST_SQ ||
							(distSqToTarget >= BELOW_SEA_LVL_BOUNCE_DIST_SQ && specP.getLocation().getY() < specP.getWorld().getSeaLevel())
						){
							pl.getLogger().info("bouncing "+specP.getName()+" towards target: "+newTarget.getName());
							Vector fromSpecToTarget = newTarget.getLocation().subtract(specP.getLocation()).toVector();
							Vector bounceBackV = fromSpecToTarget.normalize();
							bounceBackV.multiply(new Vector(.8, 10, .8));
							specP.setVelocity(bounceBackV);
						}
						else if(!specP.hasPermission("hardcore.spectator.bypass.antixray") && canSeeThroughBlocks(specP.getEyeLocation())){
							if(ANTI_XRAY_SEE_THRU && makeSeeThru(specP, specP.getEyeLocation())){
								specP.addScoreboardTag("cleanup_see_thru");
								if(needSeeThruCleanup){
									cleanupSeeThru(specP, specP.getEyeLocation(), SPEC_SEE_THRU_RADIUS);
									needSeeThruCleanup = false;
								}
							}
							else if(specP.hasPermission("hardcore.spectator.bypass.antixray.ifclose") && distSqToTarget < 3*3){
								// all good
							}
							else{
								Location closestAir = EvUtils.getClosestBlock(specP.getEyeLocation(), 10,
										(Block b) -> {return !b.getType().isOccluding();});
								if(closestAir != null){
									closestAir.add(.5, -1, .5);
									specP.teleport(closestAir, TeleportCause.CHORUS_FRUIT);
									specP.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20*5, 0, true));
								}
								else specP.teleport(newTarget, TeleportCause.CHORUS_FRUIT);
							}
						}
					}
				}
				if(needSeeThruCleanup){
					cleanupSeeThru(specP, specP.getEyeLocation(), 0);
				}
			}
		}}.runTaskTimer(pl, 20, 20);//delay, freq
	}

	/*@EventHandler
	public void onSpectatePlayer(PlayerSpectateEvent evt){
		//TODO: prevent spectators spectating spectators? (bypasses whitelist)
		//TODO: set specP inv contents <- target inv contents (empty if target == null)
	}*/
	private boolean isSpectating(Location spectatorLoc, Player target){
		return target != null && target.getWorld().getUID().equals(spectatorLoc.getWorld().getUID())
				&& target.getLocation().distanceSquared(spectatorLoc) < 50*50;
	}

	private HashMap<Pair<UUID, UUID>, BukkitTask> recentStartedSpectating = new HashMap<>();
	private void sendSpectatorNotices(Player spectator, Player newTarget, Location from, Location to){
		Pair<UUID, UUID> key = new Pair<>(spectator.getUniqueId(), newTarget == null ? null : newTarget.getUniqueId());
		BukkitTask oldTimeout = recentStartedSpectating.put(key,
			new BukkitRunnable(){@Override public void run(){
				recentStartedSpectating.remove(key);
				if(from != null){
					Player oldTarget = getClosestGm0WithPerms(from, /*spectator=*/spectator);
					if(oldTarget != null) sendSpectateEndNotice(spectator, oldTarget, /*leftTo=*/to);
				}
			}}.runTaskLater(pl, 20*5)//Must leave and stay gone for 5 seconds to send a "no longer spectating" notice.
		);
		// Cannot send a "is now spectating" notice if already sent (and hasn't sent "no longer spectating")
		if(oldTimeout == null){
			if(newTarget != null) sendSpectateBeginNotice(spectator, newTarget, /*cameFrom=*/from);
		}
		else oldTimeout.cancel();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onSpectateTeleport(PlayerTeleportEvent evt){
		if(!isSpectatorFavorYes(evt.getPlayer()) || evt.getPlayer().hasPermission("hardcore.spectator.bypass.tpcheck")) return;
		///TODO: TEMP CODE FOR ARCHERY EVT //////////////////////////////////////////////
		if(evt.getTo().getX() < -29990000 && evt.getTo().getZ() < -29990000){
			evt.getPlayer().sendMessage(ChatColor.RED+"You cannot enter this area while in specator mode");
			evt.setCancelled(true);
			return;
		}///////////////////////////////////////////////////////////////////////////////

		if(evt.getCause() == TeleportCause.CHORUS_FRUIT) return;
		if(evt.getCause() == TeleportCause.SPECTATE && evt.getTo().distanceSquared(SPECTATOR_BOX) < 5D) return;
		Player newTarget = getClosestGm0WithPerms(evt.getTo(), /*spectator=*/evt.getPlayer());
		if(!isSpectating(evt.getTo(), newTarget)){
			evt.getPlayer().sendMessage(ChatColor.RED+"No valid/permitted target found at teleport("+evt.getCause()+") destination");
			pl.getLogger().info("No target for "+evt.getPlayer()+" at destination: "+
					evt.getTo().getWorld().getName()+", "+
					evt.getTo().getBlockX()+", "+evt.getTo().getBlockY()+", "+evt.getTo().getBlockZ());
			evt.setCancelled(true);
			return;
		}
		if(EvUtils.crossDimensionalDistanceSquared(evt.getFrom(), evt.getTo()) >= SEND_NOTICE_DIST_SQ){
			sendSpectatorNotices(evt.getPlayer(), newTarget, evt.getFrom(), evt.getTo());
		}
		//evt.getPlayer().setSpectatorTarget(p);
		//pl.getLogger().info(evt.getPlayer().getName()+" is now spectating "+target.getName());
	}

	public boolean addSpectator(Player player){
		if(player.getGameMode() == GameMode.SPECTATOR){
			pl.getServer().getScoreboardManager().getMainScoreboard().getTeam("Spectators").addEntry(player.getName());
		}
		if(isSpectator(player) && spectators.add(player.getUniqueId())){
			player.addScoreboardTag("spectating");
			if(!player.hasPermission("hardcore.spectator.bypass.blindness"))
				player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1000000, 0, true));
			if(!player.hasPermission("hardcore.spectator.bypass.slowness")) player.setFlySpeed(FLY_SPEED);
			runSpectatorLoop();
			pl.getLogger().info("Added spectator: "+player.getName());
			return true;
		}
		return false;
	}
	public boolean removeSpectator(Player player){
		if(!player.hasPermission("hardcore.spectator.bypass.slowness")) player.getPlayer().setFlySpeed(0.2f);
		player.removeScoreboardTag("spectating");
		pl.getServer().getScoreboardManager().getMainScoreboard().getTeam("Spectators").removeEntry(player.getName());
		if(spectators.remove(player.getUniqueId())){
			pl.getLogger().fine("Removed spectator: "+player.getName());
			return true;
		}
		return false;
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent evt){
		if(removeSpectator(evt.getPlayer())){
			// Eh, if they logged out people can deduce that they are no longer spectating
			//Player oldTarget = getClosestGm0WithPerms(evt.getPlayer().getLocation(), /*spectator=*/evt.getPlayer());
			//if(oldTarget != null){
			//	if(oldTarget != null) sendSpectateEndNotice(/*spectator=*/evt.getPlayer(), oldTarget, /*leftTo=*/null);
			//	//sendSpectatorNotices(/*spectator=*/evt.getPlayer(), /*newTarget=*/null, /*from=*/evt.getPlayer().getLocation(), /*to=*/null);
			//}
			evt.getPlayer().getScoreboard().resetScores(evt.getPlayer().getName());
			int ticksSinceDeath = evt.getPlayer().getStatistic(Statistic.TIME_SINCE_DEATH);
			long secondsSinceDeath = ticksSinceDeath/20;
//			pl.getLogger().info("Ticks since death: "+ticksSinceDeath);
			pl.getLogger().info("Hours since death: "+(secondsSinceDeath/(60*60)));
		}
	}

	@EventHandler
	public void onLogin(PlayerLoginEvent evt){ // PlayerLoginEvent is useful because getLastPlayed() returns last disconnect ts
		final UUID uuid = evt.getPlayer().getUniqueId();
		OfflinePlayer offP = pl.getServer().getOfflinePlayer(uuid);
		final long millisSinceLastLogin = System.currentTimeMillis() - offP.getLastPlayed();
		final long ticksSinceLastLogin = millisSinceLastLogin/50;
		if(ticksSinceLastLogin <= 0) pl.getLogger().warning("Unexpected ticksSinceLastLogin: "+ticksSinceLastLogin);

		if(!ALLOW_SPECTATORS){
			long secondsLeft = HCTweaks.secondsLeftUntilRespawn(evt.getPlayer(), ticksSinceLastLogin/20);
			if(secondsLeft <= 0) pl.resetPlayer(evt.getPlayer());
			else{
				evt.setKickMessage(ChatColor.RED+"Spectating is currently disabled\n"
						+ChatColor.GRAY+"You can respawn in "+formatTimeUntilRespawn(secondsLeft, ChatColor.GOLD, ChatColor.GRAY));
				evt.setResult(Result.KICK_OTHER);
			}
			return;
		}
		new BukkitRunnable(){@Override public void run(){
			Player p = pl.getServer().getPlayer(uuid);
			if(p != null && isSpectatorFavorYes(p)){
				String waitTimeAdded = TextUtils.formatTime(millisSinceLastLogin, /*show0s=*/false);
				pl.getLogger().info("Adding: "+waitTimeAdded+" to TIME_SINCE_DEATH statistic (ticks="+ticksSinceLastLogin+")");
				p.incrementStatistic(Statistic.TIME_SINCE_DEATH, (int)ticksSinceLastLogin);
			}
		}}.runTaskLater(pl, 20);
	}
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onJoin(PlayerJoinEvent evt){
		if(addSpectator(evt.getPlayer())){
			Player newTarget = getClosestGm0WithPerms(evt.getPlayer().getLocation(), /*spectator=*/evt.getPlayer());
			// Already done in onSpectatorTeleport() by runSpectatorLoop() if dist >= TP_DIST_SQ
			if(newTarget != null && EvUtils.crossDimensionalDistanceSquared(evt.getPlayer().getLocation(), newTarget.getLocation()) < TP_DIST_SQ){
				sendSpectatorNotices(/*spectator=*/evt.getPlayer(), newTarget, /*from=*/null, /*to=*/evt.getPlayer().getLocation());
			}
		}
	}

	@EventHandler
	public void onGameModeChange(PlayerGameModeChangeEvent evt){
		new BukkitRunnable(){@Override public void run(){
			if(evt.getNewGameMode() == GameMode.SPECTATOR) addSpectator(evt.getPlayer());
			else removeSpectator(evt.getPlayer());
		}}.runTaskLater(pl, 1);
	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent evt){
		if(!evt.getPlayer().getScoreboardTags().contains("dead")) return; // PlayerRespawnEvent is also be called when leaving the end, etc.
		pl.getLogger().warning(evt.getPlayer().getName()+" pressed respawn/spectate world");

		pl.getServer().getScoreboardManager().getMainScoreboard().getTeam("Dead").removeEntry(evt.getPlayer().getName());

		if(!ALLOW_SPECTATORS){
			long secondsLeft = HCTweaks.secondsLeftUntilRespawn(evt.getPlayer());
			evt.getPlayer().kickPlayer(ChatColor.RED+"Spectating is currently disabled\n"
						+ChatColor.GRAY+"You can respawn in "+formatTimeUntilRespawn(secondsLeft, ChatColor.GOLD, ChatColor.GRAY));
			return;
		}

		Extras.grantLocationBasedAdvancements(evt.getPlayer(), /*silently=*/true);
		if(SYNC_SPECTATOR_INVS) Extras.grantItemBasedAdvancements(evt.getPlayer(), /*silently=*/true);

		final UUID uuid = evt.getPlayer().getUniqueId();
		new BukkitRunnable(){@Override public void run(){
			Player p = pl.getServer().getPlayer(uuid);
			if(p != null) addSpectator(p);
		}}.runTaskLater(pl, 20*5);
	}

	@EventHandler
	public void onPreCommand(PlayerCommandPreprocessEvent evt){
		if(evt.getMessage().charAt(0) != '/') return;
		String message = evt.getMessage().trim();
		String command = message.toLowerCase();
		int space = command.indexOf(' ');
		command = (space > 0 ? command.substring(1, space) : command.substring(1));
		Player player = evt.getPlayer();

		if(command.equals("tp") && message.indexOf(' ', space+1) == -1){
			if(isSpectator(player)){
				evt.setCancelled(true);
				Player target = null;
				if(space < 0 || (target=pl.getServer().getPlayer(message.substring(space + 1))) == null){
					player.sendMessage(ChatColor.RED+"Please specify who you wish to tp to (exact username)");
					player.sendMessage("Note: you can also use vanilla spectator menu (press 1)");
					return;
				}
				if(!player.isOp() && !SpectatorManager.canSpectate(player.getUniqueId(), target)){
					if(getSpectateMode(target) == WatchMode.WHITELIST)
						player.sendMessage(ChatColor.GRAY+target.getDisplayName()+
								ChatColor.RED+" has not added you to their spectator whitelist");
					else if(getSpectateMode(target) == WatchMode.BLACKLIST)
						player.sendMessage(ChatColor.GRAY+target.getDisplayName()+
								ChatColor.RED+" has you on their spectator blacklist");
				}
				else{
					player.teleport(target, TeleportCause.COMMAND);
					player.setSpectatorTarget(target);
				}
			}
		}
	}

	/*@EventHandler(priority = EventPriority.MONITOR)
	public void onChat(AsyncPlayerChatEvent evt){//TODO: &9[&7Dead&9] prefix?
		if(!SpectatorManager.isSpectator(evt.getPlayer()) || evt.isCancelled()) return;
	}*/

	/*@EventHandler //TODO: isn't triggered by spectators opening their inventory???
	public void onInventoryOpen(InventoryOpenEvent evt){
		Player player = (Player)evt.getPlayer();
		Entity entity = player.getSpectatorTarget();
		pl.getLogger().info("player who opened: "+evt.getPlayer().getName()+", spectate target: "+(entity == null ? "null" : entity.getName()));
		if(entity == null || entity instanceof Player == false || !SpectatorManager.isSpectator(player)) return;
		pl.getLogger().info("with spectator target");
		if(evt.getInventory().getType() == InventoryType.PLAYER){
			pl.getLogger().info("inv type = PLAYER");
			Player target = (Player)entity;
			player.getInventory().setContents(target.getInventory().getContents());
			player.getInventory().setArmorContents(target.getInventory().getArmorContents());
			player.getInventory().setItemInOffHand(target.getInventory().getItemInOffHand());
		}
		//TODO: crafting table inventory?
	}*/
}
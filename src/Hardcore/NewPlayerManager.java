package Hardcore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.EndGateway;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TextUtils;

public class NewPlayerManager implements Listener{
	final HCTweaks pl;
	final double EULERS_CONSTANT = 0.57721566490153286060651209d;
	final int PRE_GEN_SPAWNS, MAX_NEW_ACCS_ON_SAME_IP;
	final ItemStack GUIDE_BOOK;
	final ArrayDeque<Location> spawnLocs;
	final String WORLD_NAME, SPAWN_MSG, RESPAWN_MSG;
	final HashMap<UUID, Listener> mobSpawnListeners;

	// Prevent new players from being greated by a swarm of hostile mobs when they exit the bedrock box
	class HostileMobPreventer implements Listener{
		final Location location;
		final double radiusSquared;
		HostileMobPreventer(Location loc, double r){location = loc; radiusSquared = r*r;}
		@EventHandler public void onHostileMobSpawn(CreatureSpawnEvent evt){
			if(!evt.isCancelled() && evt.getEntity() instanceof Monster && evt.getSpawnReason() == SpawnReason.NATURAL){
				Location entityLoc = evt.getEntity().getLocation();
				if(entityLoc.getWorld().getUID().equals(location.getWorld().getUID()) && entityLoc.distanceSquared(location) < radiusSquared){
					evt.setCancelled(true);
				}
			}
		}
	}

	public NewPlayerManager(HCTweaks plugin){
		pl = plugin;
		mobSpawnListeners = new HashMap<>();
		WORLD_NAME = pl.getConfig().getString("world-name", "Reliquist");
		World hardcoreWorld = pl.getServer().getWorld(WORLD_NAME);
		Block chestBlock = hardcoreWorld.getBlockAt(0, 1, 0);
		if(chestBlock.getState() instanceof BlockInventoryHolder) {
			Container chest = (Container)chestBlock.getState();
			ItemStack book = null;
			for(ItemStack item : chest.getInventory().getContents()){
				if(item != null && item.getType() == Material.WRITTEN_BOOK) {
					book = item;
					break;
				}
			}
			GUIDE_BOOK = book;
		}
		else GUIDE_BOOK = null;
		if(GUIDE_BOOK == null) pl.getLogger().warning("Unable to find starter book");
		spawnLocs = new ArrayDeque<Location>();
		for(String str : FileIO.loadFile("pre-gen-spawns.txt", "0.5,75,0.5").split("\n")){
			Location loc = TextUtils.getLocationFromString(hardcoreWorld, str);
			if(loc != null) spawnLocs.add(loc);
		}
		PRE_GEN_SPAWNS = pl.getConfig().getInt("prepared-spawn-locations", 5);
		if(spawnLocs.size() < PRE_GEN_SPAWNS) {
			pl.getLogger().info("Pre-Generating " + (PRE_GEN_SPAWNS - spawnLocs.size()) + " spawnpoints...");
			while(spawnLocs.size() < PRE_GEN_SPAWNS) spawnLocs.add(getRandomSpawnLoc());
			saveSpawnLocs();
		}
		SPAWN_MSG = TextUtils.translateAlternateColorCodes('&',
				pl.getConfig().getString("spawn-welcome-message", "&bWelcome, &7%name%&b!"));
		RESPAWN_MSG = TextUtils.translateAlternateColorCodes('&',
				pl.getConfig().getString("respawn-welcome-message", "&bWelcome, &7%name%&b!"));
		MAX_NEW_ACCS_ON_SAME_IP = pl.getConfig().getInt("max-new-accounts-per-day-with-same-ip", 3);
	}

	void saveSpawnLocs(){
		FileIO.saveFile("pre-gen-spawns.txt", StringUtils.join(
				spawnLocs.stream()
				.map(loc -> loc.getBlockX()+","+loc.getBlockY()+","+loc.getBlockZ())
				.iterator(), '\n'));
	}

	boolean hasNearbyLava(Location loc){
		for(int x=-15; x<=15; ++x) for(int y=-10; y<=10; ++y) for(int z=-15; z<=15; ++z){
			Block block = loc.clone().add(x, y, z).getBlock();
			if(block != null && block.getType() == Material.LAVA) return true;
		}
		return false;
	}
	boolean isOverLiquid(Location loc){
		Block b = loc.getBlock();
		while(b != null && b.isEmpty() && b.isPassable() && !b.isLiquid()) b = b.getRelative(BlockFace.DOWN);
		return b.isLiquid();
	}
	double getRandomCoord(double maxCoord, double scale, double randDouble){
		//Into wolfram: (e^(p*11.58 - 0.57721566490) - 0.5)*500
		pl.getLogger().info("Generating random coord, scale="+scale);
		double maxHn = Math.log(maxCoord/scale + 0.5) + EULERS_CONSTANT;
		double coord = (Math.exp(randDouble*maxHn - EULERS_CONSTANT) - 0.5)*scale;
		pl.getLogger().info("Max N="+Math.round(maxCoord)
				+", Max Hn="+(Math.round(maxHn*100d)/100d)
				+", Rand="+(Math.round(randDouble*100d)/100d)
				+", Hn="+(Math.round(randDouble*maxHn*100d)/100d)
				+", N="+Math.round(coord));
		return coord;
	}
	// Warning: Very laggy!  Call asynchronously when possible
	Location getRandomSpawnLoc(){
		final Random rand = new Random();
		final World world = pl.getServer().getWorld(WORLD_NAME);
		final WorldBorder border = world.getWorldBorder();
		final Location origin = border.getCenter();
		final int seaLevel = world.getSeaLevel();
		final double maxOffset = border.getSize()/2;
		final double stdDev = maxOffset/4;
		//final double borderHn = Math.log(maxOffset + 0.5) + EULERS_CONSTANT;
		while(true){
			//double x = (rand.nextGaussian() * rand.nextGaussian()) * stdDev;
			//double z = (rand.nextGaussian() * rand.nextGaussian()) * stdDev;
			double x = getRandomCoord(maxOffset, 500, rand.nextDouble());
			double z = getRandomCoord(maxOffset, 500, rand.nextDouble());
			while(Math.abs(x) > maxOffset) x = rand.nextGaussian() * stdDev;
			while(Math.abs(z) > maxOffset) z = rand.nextGaussian() * stdDev;
			x = ((long)x/16)*16 - 8;// Make sure we are in the center of a chunk
			z = ((long)z/16)*16 - 8;
			x = Math.floor(origin.getX() + x) + 0.5d;
			z = Math.floor(origin.getZ() + z) + 0.5d;
//			if(isOnChunkBoundary((long)x, (long)z)) continue;
			if(rand.nextBoolean()) x = -x;
			if(rand.nextBoolean()) z = -z;

			Location loc = new Location(world, x, 250, z);
			if(!loc.getChunk().load(true)){
				pl.getLogger().severe("Failed to generate spawnLoc chunk!");
				return null;
			}

			while(loc.getBlockY() > seaLevel && (loc.getBlock() == null || loc.getBlock().isEmpty())) loc.setY(loc.getY() - 1);
			String debugStr = "Candidate X,Y,Z: "+loc.getBlockX()+" "+loc.getBlockY()+" "+loc.getBlockZ();

			if(loc.getY() < seaLevel) pl.getLogger().info(debugStr+" >> Below sea level (Y="+loc.getBlockY()+")");
			else if(isOverLiquid(loc)) pl.getLogger().info(debugStr+" >> Over liquid");
			else if(hasNearbyLava(loc)) pl.getLogger().info(debugStr+" >> Near to lava");
			else{
				pl.getLogger().info(debugStr+" >> SUCCESS");
				loc.setY(loc.getY() + 3.02); // Two blocks above the ground
				return loc;
			}
		}
	}

	static void removeSpawnBox(Location loc, Player player){
		if(loc == null && player != null) loc = player.getLocation();
		for(int x=-6; x<=6; ++x) for(int y=-6; y<=6; ++y) for(int z=-6; z<=6; ++z){
			Block block = loc.clone().add(x, y, z).getBlock();
			if(block == null) continue;
			switch(block.getType()){
				case BARRIER:
				case BEDROCK:
				case END_GATEWAY:
					block.setType(Material.AIR);
					break;
				default:
					// Remove any "fake" blocks
					player.sendBlockChange(block.getLocation(), block.getBlockData());
			}
		}
	}

	void putQuickBedrock(Location loc){
		loc.getBlock().getRelative(BlockFace.UP).setType(Material.BEDROCK);
		loc.getBlock().getRelative(BlockFace.DOWN).setType(Material.BEDROCK);
		loc.getBlock().getRelative(BlockFace.NORTH).setType(Material.BEDROCK);
		loc.getBlock().getRelative(BlockFace.SOUTH).setType(Material.BEDROCK);
		loc.getBlock().getRelative(BlockFace.EAST).setType(Material.BEDROCK);
		loc.getBlock().getRelative(BlockFace.WEST).setType(Material.BEDROCK);
		for(int x=-3; x<=3; ++x) for(int y=-3; y<=3; ++y) for(int z=-3; z<=3; ++z){
			Block block = loc.getBlock().getRelative(x, y, z);
			if(block.isEmpty()) block.setType(Material.BEDROCK);
		}
	}
	void createSpawnBox(Location loc, Player player){
		if(loc == null && player != null) loc = player.getLocation();
		// Always set the 6 faces
		loc.getBlock().getRelative(BlockFace.UP).setType(Material.BARRIER);
		loc.getBlock().getRelative(BlockFace.DOWN).setType(Material.BARRIER);
		loc.getBlock().getRelative(BlockFace.NORTH).setType(Material.BARRIER);
		loc.getBlock().getRelative(BlockFace.SOUTH).setType(Material.BARRIER);
		loc.getBlock().getRelative(BlockFace.EAST).setType(Material.BARRIER);
		loc.getBlock().getRelative(BlockFace.WEST).setType(Material.BARRIER);

		// EndGateways
		int[] xs = new int[]{2,0,0, -2,-0,-0,  1,1,0, -1,-1,-0,  +1,-1,+1,-1,+0,+0};
		int[] ys = new int[]{0,2,0, -0,-2,-0,  1,0,1, -1,-0,-1,  -1,+1,+0,+0,+1,-1};
		int[] zs = new int[]{0,0,2, -0,-0,-2,  0,1,1, -0,-1,-1,  +0,+0,-1,+1,-1,+1};
		for(int i=0; i<xs.length; ++i){
			Block block = loc.getBlock().getRelative(xs[i], ys[i], zs[i]);
			switch(block.getType()){
				case AIR:
				case BEDROCK:
				case BARRIER:
				case END_GATEWAY:
					block.setType(Material.END_GATEWAY);
					EndGateway gatewayState = (EndGateway)block.getState();
					gatewayState.setAge(-1000000000);
					gatewayState.update(true);
					break;
				default:
					if(player != null) player.sendBlockChange(player.getLocation().add(xs[i], ys[i], zs[i]), Material.END_GATEWAY.createBlockData());
			}
		}

		// Fill around the sides with bedrock
		for(int x=-3; x<=3; ++x) for(int y=-3; y<=3; ++y) for(int z=-3; z<=3; ++z){
			Block block = loc.getBlock().getRelative(x, y, z);
			if(block.isEmpty()) block.setType(Material.BEDROCK);
			/*else if(player != null){
				switch(block.getType()){
					case BARRIER:
					case BEDROCK:
					case END_GATEWAY:
						break;
					default:
						player.sendBlockChange(block.getLocation(), Material.BEDROCK.createBlockData());
				}
			}*/
		}
		loc.getBlock().setType(Material.AIR);
	}

	public void giveGuideBook(Player player){
		ItemStack myGuide = GUIDE_BOOK.clone();
		BookMeta meta = (BookMeta) myGuide.getItemMeta();
		List<String> lore = meta.getLore();
		lore.add(ChatColor.GRAY+"Owner: "+player.getName());
		String dateStr = new SimpleDateFormat("yyy-MM-dd").format(new Date());
		lore.add(ChatColor.GRAY+"Printed: "+dateStr);
		meta.setLore(lore);
		/*int idx = 0;
		for(String page : meta.getPages()){
			pl.getLogger().info("Page "+idx+": "+page);
			if(page.contains("%name%")) meta.setPage(++idx, page.replaceAll("%name%", player.getName()));
		}*/
		myGuide.setItemMeta(meta);
		player.getInventory().setItemInMainHand(myGuide);
	}

	public void spawnNewPlayer(Player player){
//		pl.getLogger().warning("Spawning new player: "+player.getName());
		final UUID uuid = player.getUniqueId();
		player.addScoreboardTag("unconfirmed");
		String Adv0TeamName = ScoreboardManager.getAdvancementTeamName(0);
		Team newTeam = player.getScoreboard().getTeam(Adv0TeamName);
		if(newTeam == null) newTeam = player.getScoreboard().registerNewTeam(Adv0TeamName);
		newTeam.addEntry(player.getName());

		Location spawnLoc = spawnLocs.remove();
		spawnLoc.setX(spawnLoc.getBlockX() + 0.5);
		spawnLoc.setZ(spawnLoc.getBlockZ() + 0.5);
		pl.getLogger().warning("Spawning new player '"+player.getName()+"' at: "
				+ TextUtils.locationToString(spawnLoc, ChatColor.GREEN, ChatColor.YELLOW, 0));
		saveSpawnLocs();
		new BukkitRunnable(){@Override public void run(){
			Location spawnLoc = getRandomSpawnLoc();
			spawnLocs.add(spawnLoc);
			saveSpawnLocs();
		}}.runTaskLater/*Asynchronously*/(pl, 20*60);//60s

		final boolean announceAdvDefault = player.getWorld().getGameRuleDefault(GameRule.ANNOUNCE_ADVANCEMENTS);
		player.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
		createSpawnBox(spawnLoc, player);
		player.teleport(spawnLoc);
		new BukkitRunnable(){@Override public void run(){
			Player player = pl.getServer().getPlayer(uuid);
			if(player != null && player.getScoreboardTags().contains("unconfirmed")){
				player.teleport(spawnLoc);
				createSpawnBox(spawnLoc, player);
				player.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, announceAdvDefault);
			}
		}}.runTaskLater(pl, 20);
		player.setBedSpawnLocation(spawnLoc);
		player.setWalkSpeed(0f);
		giveGuideBook(player);

		File deathDir = new File("./plugins/EvFolder/deaths/"+player.getName());
		if(deathDir.exists()){
			int numDeaths = deathDir.listFiles().length;
			pl.getLogger().info(player.getName()+" has "+numDeaths+" deaths");
			player.setStatistic(Statistic.DEATHS, numDeaths);
			String deathStats = FileIO.loadFile("deaths/"+player.getName()+"/death-stats.txt", "");
			for(String line : deathStats.split("\n")){
				int i = line.indexOf(',');
				if(i != -1){
					EntityType killedByEntity = EntityType.valueOf(line.substring(0, i).trim());
					int numTimesKilled = Integer.parseInt(line.substring(i+1).trim());
					player.setStatistic(Statistic.ENTITY_KILLED_BY, killedByEntity, numTimesKilled);
				}
			}
			new BukkitRunnable(){@Override public void run(){
				//player.setStatistic(Statistic.DEATHS, numDeaths);//TODO: is this line needed?
				ScoreboardManager.setNumDeaths(player, numDeaths);
			}}.runTaskLater(pl, 20);
			pl.getServer().broadcastMessage(RESPAWN_MSG.replaceAll("%name%", player.getName()));
			player.sendTitle(/*title=*/"", /*subtitle=*/"§7§lWelcome back to §6§lH§ea§6§lrd§ec§6§lore§7§l!",
					/*fadeIn=*/10, /*stay=*/20*15, /*fadeOut=*/20);
		}
		else{
			pl.getServer().broadcastMessage(SPAWN_MSG.replaceAll("%name%", player.getName()));
			player.sendTitle(/*title=*/"§7§lWelcome to §6§lH§ea§6§lrd§ec§6§lore§7§l!",
					/*subtitle=*/"§9Read the book to be set free and spawn in the world",
					/*fadeIn=*/10, /*stay=*/20*15, /*fadeOut=*/20);
		}

		//TODO: new permissions plugin. This is garbage.
		//pl.runCommand("perms player addgroup "+player.getName()+" default");
	}

	// Prevent broadcasting "Remote Getaway" advancement for players that lag when they join
	/*@EventHandler
	public void blockAdvancement(final PlayerAdvancementDoneEvent evt){
		if(evt.getPlayer().getScoreboardTags().contains("unconfirmed")){
			for(String criteria : evt.getAdvancement().getCriteria()){
				evt.getPlayer().getAdvancementProgress(evt.getAdvancement()).revokeCriteria(criteria);
			}
		}
	}*/

	HashMap<String, Integer> newPlayerCountByIP = new HashMap<>();
	@EventHandler
	public void onPlayerLogin(PlayerLoginEvent evt){
		final UUID uuid = evt.getPlayer().getUniqueId();
		OfflinePlayer offP = pl.getServer().getOfflinePlayer(uuid);
		if(!offP.hasPlayedBefore()){
			String playerIP = evt.getAddress().getHostAddress();
			int numNewPlayersOnIP = newPlayerCountByIP.getOrDefault(playerIP, 0) + 1;
			if(numNewPlayersOnIP > MAX_NEW_ACCS_ON_SAME_IP){
				evt.setKickMessage(ChatColor.RED+"Too many new players have joined from your IP recently..\nTry again tomorrow");
				evt.setResult(Result.KICK_OTHER);
			}
			else newPlayerCountByIP.put(playerIP, numNewPlayersOnIP);
		}
	}
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent evt){
		final Player player = evt.getPlayer();
		// Newly (re)spawned player
		if(player.getScoreboardTags().isEmpty()) spawnNewPlayer(player);

		// Unconfirmed new player
		if(player.getScoreboardTags().contains("unconfirmed")){
			player.setInvulnerable(true);
			player.setGravity(false);
			Location spawnLoc = player.getLocation();
//			while(spawnLoc.getBlock().isEmpty()) spawnLoc.setY(spawnLoc.getY() - 1); spawnLoc.setY(spawnLoc.getY() + 3.02);
//			spawnLoc.setX((int)(Math.round(spawnLoc.getX()*2)/2));
//			spawnLoc.setZ((int)(Math.round(spawnLoc.getZ()*2)/2));
//			player.teleport(spawnLoc);
			putQuickBedrock(spawnLoc);
//			player.teleport(spawnLoc);
			player.sendMessage(ChatColor.GREEN+">> "+ChatColor.GOLD+ChatColor.BOLD+"Read the book to get started");
			createSpawnBox(spawnLoc, player);
			new BukkitRunnable(){@Override public void run(){
				if(player.getScoreboardTags().contains("unconfirmed")) createSpawnBox(spawnLoc, player);}}.runTaskLater(pl, 1*20);
			new BukkitRunnable(){@Override public void run(){
				if(player.getScoreboardTags().contains("unconfirmed")) createSpawnBox(spawnLoc, player);}}.runTaskLater(pl, 2*20);
			new BukkitRunnable(){@Override public void run(){
				if(player.getScoreboardTags().contains("unconfirmed")) createSpawnBox(spawnLoc, player);}}.runTaskLater(pl, 4*20);
			player.getInventory().clear();
			giveGuideBook(player);
			HostileMobPreventer spawnPreventer = new HostileMobPreventer(player.getLocation(), 200);
			pl.getServer().getPluginManager().registerEvents(spawnPreventer, pl);
			mobSpawnListeners.put(evt.getPlayer().getUniqueId(), spawnPreventer);
			
		}

		// Update tags
		// TODO: should color nick be saved?
		if(player.getScoreboardTags().contains("color_nick")){
			ChatColor color = TextUtils.getCurrentColor(player.getDisplayName());
			if(color != null){
				new BukkitRunnable(){@Override public void run(){
					pl.runCommand("nick "+player.getName()+" &"+color.getChar()+player.getName());
				}}.runTaskLater(pl, 20);
			}
		}
		if(!player.isOp()){
//			pl.setPermission(player, "essentials.tpa", player.getScoreboardTags().contains("has_tpa"));
//			pl.setPermission(player, "essentials.tpahere", player.getScoreboardTags().contains("has_tpahere"));
//			pl.setPermission(player, "essentials.tpaccept", player.getScoreboardTags().contains("has_tpaccept"));
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent evt){
		if(evt.getPlayer().getScoreboardTags().contains("unconfirmed")){
			removeSpawnBox(evt.getPlayer().getLocation(), evt.getPlayer());
			mobSpawnListeners.remove(evt.getPlayer().getUniqueId());
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPreCommand(PlayerCommandPreprocessEvent evt){
		final String command = evt.getMessage().trim().toLowerCase();
		if(command.equals("/accept-terms") && evt.getPlayer().removeScoreboardTag("unconfirmed")){
			// This is the silent way to remove this advancement (in case it was erroneously given)
			NamespacedKey key = NamespacedKey.minecraft("end/enter_end_gateway");
			AdvancementProgress progress = evt.getPlayer().getAdvancementProgress(pl.getServer().getAdvancement(key));
			for(String criteria : progress.getRemainingCriteria()) progress.revokeCriteria(criteria);
			// Alternative, but considered extra console spam
			//pl.runCommand("minecraft:advancement revoke "+evt.getPlayer().getName()+" only minecraft:end/enter_end_gateway");
			Player player = evt.getPlayer();
			evt.setCancelled(true);
			player.setGravity(true);
			player.setWalkSpeed(0.2f);
			removeSpawnBox(player.getLocation(), player);
			player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 10, 3), true);
			player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10, 3), true);
			player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 2, 0), true);
			player.setInvulnerable(false);
			player.setSaturation(20);
			player.setHealth(evt.getPlayer().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
			player.addScoreboardTag("joined");
//			player.addScoreboardTag("has_tpahere");
//			player.addScoreboardTag("has_tpa");
//			player.addScoreboardTag("has_tpaccept");
			player.addScoreboardTag("autoquit_ping_3000");
//			pl.setPermission(player, "essentials.tpa", true);
//			pl.setPermission(player, "essentials.tpahere", true);
//			pl.setPermission(player, "essentials.tpaccept", true);
			mobSpawnListeners.remove(evt.getPlayer().getUniqueId());
		}
		else if(evt.getPlayer().getScoreboardTags().contains("unconfirmed")){
			List<String> whitelistedCommands = Arrays.asList("/help", "/rules", "/ping", "/gc", "/ignore");
			if(whitelistedCommands.contains(command)) return;
			evt.getPlayer().sendMessage(ChatColor.RED+"You must read the book before you can do that");
			evt.setCancelled(true);
		}
		else if(evt.getPlayer().getScoreboardTags().contains("dead") && command.equals("/respawn")){
			evt.setCancelled(true);
			long secondsLeft = HCTweaks.secondsLeftUntilRespawn(evt.getPlayer());
			if(secondsLeft > 0){
				evt.getPlayer().sendMessage(ChatColor.RED+"You cannot use that command yet");
				evt.getPlayer().sendMessage(SpectatorManager.formatTimeUntilRespawn(secondsLeft, ChatColor.GOLD, ChatColor.GRAY));
			}
			else pl.resetPlayer(evt.getPlayer());
		}
	}
}
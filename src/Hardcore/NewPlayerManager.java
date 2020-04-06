package Hardcore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import net.evmodder.EvLib.EvUtils;
import net.evmodder.EvLib.FileIO;
import net.evmodder.EvLib.extras.TextUtils;

public class NewPlayerManager implements Listener{
	final HCTweaks pl;
	final double EULERS_CONSTANT = 0.57721566490153286060651209d;
	final int PRE_GEN_SPAWNS;
	final ItemStack GUIDE_BOOK;
	final ArrayDeque<Location> spawnLocs;
	final String WORLD_NAME, SPAWN_MSG, RESPAWN_MSG;
	final long SECONDS_UNTIL_RESPAWN;

	public NewPlayerManager(HCTweaks plugin){
		pl = plugin;
		SECONDS_UNTIL_RESPAWN = pl.getConfig().getInt("respawn-wait", 24)*60*60;
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
			Location loc = EvUtils.getLocationFromString(hardcoreWorld, str);
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
	}

	void saveSpawnLocs(){
		FileIO.saveFile("pre-gen-spawns.txt", StringUtils.join(
				spawnLocs.stream()
				.map(loc -> loc.getBlockX()+","+loc.getBlockY()+","+loc.getBlockZ())
				.iterator(), '\n'));
	}

	boolean isOnChunkBoundary(long x, long z){
		return Math.abs(x) % 16 < 2 || Math.abs(z) % 16 < 2;
	}
	boolean hasNearbyLava(Location loc){
		for(int x=-15; x<=15; ++x) for(int y=-10; y<=10; ++y) for(int z=-15; z<=15; ++z){
			Block block = loc.clone().add(x, y, z).getBlock();
			if(block != null && block.getType() == Material.LAVA) return true;
		}
		return false;
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
			x = Math.floor(origin.getX() + x) + 0.5d;
			z = Math.floor(origin.getZ() + z) + 0.5d;
			if(isOnChunkBoundary((long)x, (long)z)) continue;
			if(rand.nextBoolean()) x = (-x)-1;//-1 to preserve isOnChunkBoundary=true
			if(rand.nextBoolean()) z = (-z)-1;

			Location loc = new Location(world, x, 250, z);
			if(!loc.getChunk().load(true)){
				pl.getLogger().severe("Failed to generate spawnLoc chunk!");
				return null;
			}

			while(loc.getBlockY() > seaLevel & (loc.getBlock() == null || loc.getBlock().isEmpty() 
					|| loc.getBlock().isPassable())) loc.setY(loc.getY() - 1);
			loc.setY(loc.getY() + 2);
			String debugStr = "Candidate X,Y,Z: "+loc.getBlockX()+" "+loc.getBlockY()+" "+loc.getBlockZ();
			if(loc.getY() < seaLevel + 3)
				pl.getLogger().info(debugStr+" >> Below sea level");
			else if(loc.getBlock().getRelative(BlockFace.DOWN).isLiquid())
				pl.getLogger().info(debugStr+" >> Over liquid");
			else if(hasNearbyLava(loc))
				pl.getLogger().info(debugStr+" >> Near to lava");
			else{
				pl.getLogger().info(debugStr+" >> SUCCESS");
				return loc;
			}
		}
	}

	static void removeNearbyBedrock(Location loc){
		for(int x=-6; x<=6; ++x) for(int y=-6; y<=6; ++y) for(int z=-6; z<=6; ++z){
			Block block = loc.clone().add(x, y, z).getBlock();
			if(block != null && block.getType() == Material.BEDROCK) block.setType(Material.AIR);
		}
	}

	void createSpawnBox(Location loc){
		// Always set the 6 faces
		loc.getBlock().getRelative(BlockFace.UP).setType(Material.BEDROCK);
		loc.getBlock().getRelative(BlockFace.DOWN).setType(Material.BEDROCK);
		loc.getBlock().getRelative(BlockFace.NORTH).setType(Material.BEDROCK);
		loc.getBlock().getRelative(BlockFace.SOUTH).setType(Material.BEDROCK);
		loc.getBlock().getRelative(BlockFace.EAST).setType(Material.BEDROCK);
		loc.getBlock().getRelative(BlockFace.WEST).setType(Material.BEDROCK);
		for(int x=-2; x<=2; ++x) for(int y=-2; y<=2; ++y) for(int z=-2; z<=2; ++z){
			Block block = loc.clone().add(x, y, z).getBlock();
			if(block.isEmpty()) block.setType(Material.BEDROCK);
		}
		loc.getBlock().setType(Material.AIR);
	}

	void sendSpawnBoxAtmosphere(Player player){
		player.sendBlockChange(player.getLocation().add(0, 1, 0), Material.BARRIER.createBlockData());
		player.sendBlockChange(player.getLocation().add(0, -1, 0), Material.BARRIER.createBlockData());
		player.sendBlockChange(player.getLocation().add(1, 0, 0), Material.BARRIER.createBlockData());
		player.sendBlockChange(player.getLocation().add(-1, 0, 0), Material.BARRIER.createBlockData());
		player.sendBlockChange(player.getLocation().add(0, 0, 1), Material.BARRIER.createBlockData());
		player.sendBlockChange(player.getLocation().add(0, 0, -1), Material.BARRIER.createBlockData());

		player.sendBlockChange(player.getLocation(), Material.END_GATEWAY.createBlockData());
		BlockData blockData = Material.END_GATEWAY.createBlockData();
		//blockData.setAge(-1000000000);
		player.sendBlockChange(player.getLocation().add(0, 2, 0), blockData);
		player.sendBlockChange(player.getLocation().add(0, -2, 0), blockData);
		player.sendBlockChange(player.getLocation().add(2, 0, 0), blockData);
		player.sendBlockChange(player.getLocation().add(-2, 0, 0), blockData);
		player.sendBlockChange(player.getLocation().add(0, 0, 2), blockData);
		player.sendBlockChange(player.getLocation().add(0, 0, -2), blockData);
		player.sendBlockChange(player.getLocation().add(1, 1, 0), blockData);
		player.sendBlockChange(player.getLocation().add(-1, 1, 0), blockData);
		player.sendBlockChange(player.getLocation().add(0, 1, 1), blockData);
		player.sendBlockChange(player.getLocation().add(0, 1, -1), blockData);
		player.sendBlockChange(player.getLocation().add(1, -1, 0), blockData);
		player.sendBlockChange(player.getLocation().add(-1, -1, 0), blockData);
		player.sendBlockChange(player.getLocation().add(0, -1, 1), blockData);
		player.sendBlockChange(player.getLocation().add(0, -1, -1), blockData);
		player.sendBlockChange(player.getLocation().add(1, 0, 1), blockData);
		player.sendBlockChange(player.getLocation().add(-1, 0, 1), blockData);
		player.sendBlockChange(player.getLocation().add(1, 0, -1), blockData);
		player.sendBlockChange(player.getLocation().add(-1, 0, -1), blockData);
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
		pl.getLogger().warning("Spawning new player: "+player.getName());
		final UUID uuid = player.getUniqueId();
		player.addScoreboardTag("unconfirmed");
		String Adv0TeamName = ScoreboardManager.getAdvancementTeamName(0);
		Team newTeam = player.getScoreboard().getTeam(Adv0TeamName);
		if(newTeam == null) newTeam = player.getScoreboard().registerNewTeam(Adv0TeamName);
		newTeam.addEntry(player.getName());

		player.setInvulnerable(true);

		Location spawnLoc = spawnLocs.remove();
		pl.getLogger().warning("Spawning in at: "+TextUtils.locationToString(spawnLoc, ChatColor.GREEN, ChatColor.YELLOW, 0));
		saveSpawnLocs();
		new BukkitRunnable(){@Override public void run(){
			Location spawnLoc = getRandomSpawnLoc();
			spawnLocs.add(spawnLoc);
			saveSpawnLocs();
		}}.runTaskLater/*Asynchronously*/(pl, 20*60);//60s

		createSpawnBox(spawnLoc);
		sendSpawnBoxAtmosphere(player);

		spawnLoc.setX(spawnLoc.getBlockX() + 0.5);
		spawnLoc.setZ(spawnLoc.getBlockZ() + 0.5);
		player.teleport(spawnLoc);
		new BukkitRunnable(){@Override public void run(){
			Player player = pl.getServer().getPlayer(uuid);
			if(player != null){
				player.teleport(spawnLoc);
				sendSpawnBoxAtmosphere(player);
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
		if(new File("./plugins/EvFolder/aug_evt/"+player.getUniqueId()+".txt").exists()){
			player.addScoreboardTag("event_participant");
		}

		//TODO: new permissions plugin. This is garbage.
		//pl.runCommand("perms player addgroup "+player.getName()+" default");
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent evt){
		final Player player = evt.getPlayer();
		// Newly (re)spawned player
		if(player.getScoreboardTags().isEmpty()) spawnNewPlayer(player);

		// Unconfirmed new player
		if(player.getScoreboardTags().contains("unconfirmed")){
			player.sendMessage(ChatColor.GREEN+">> "
					+ChatColor.GOLD+ChatColor.BOLD+"Read the book to get started");
			createSpawnBox(player.getLocation());
			sendSpawnBoxAtmosphere(player);
			new BukkitRunnable(){@Override public void run(){sendSpawnBoxAtmosphere(player);}}.runTaskLater(pl, 1*20);
			new BukkitRunnable(){@Override public void run(){sendSpawnBoxAtmosphere(player);}}.runTaskLater(pl, 2*20);
			new BukkitRunnable(){@Override public void run(){sendSpawnBoxAtmosphere(player);}}.runTaskLater(pl, 4*20);
			player.getInventory().clear();
			giveGuideBook(player);
		}

		// Update tags
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
			removeNearbyBedrock(evt.getPlayer().getLocation());
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPreCommand(PlayerCommandPreprocessEvent evt){
		if(evt.getMessage().trim().equals("/accept-terms") && evt.getPlayer().removeScoreboardTag("unconfirmed")){
			Player player = evt.getPlayer();
			evt.setCancelled(true);
			player.setWalkSpeed(0.2f);
			NewPlayerManager.removeNearbyBedrock(player.getLocation());
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
		}
		else if(evt.getPlayer().getScoreboardTags().contains("unconfirmed")){
			evt.getPlayer().sendMessage(ChatColor.RED+"You need to read the book first");
			evt.setCancelled(true);
		}
		else if(evt.getPlayer().getScoreboardTags().contains("dead") && evt.getMessage().trim().equals("/respawn")){
			int secondsSinceDeath = evt.getPlayer().getStatistic(Statistic.TIME_SINCE_DEATH) / 20;
			if(secondsSinceDeath < SECONDS_UNTIL_RESPAWN){
				evt.getPlayer().sendMessage(ChatColor.RED+"You cannot use that command yet");
				evt.getPlayer().sendMessage(SpectatorManager.
						formatTimeUntilRespawn(SECONDS_UNTIL_RESPAWN - secondsSinceDeath, ChatColor.GOLD, ChatColor.GRAY));
				return;
			}
			pl.runCommand("essentials:nick "+evt.getPlayer().getName()+" off");
			evt.getPlayer().resetTitle();
			evt.getPlayer().kickPlayer(ChatColor.GOLD+"Resetting playerdata...\n"+
					ChatColor.GRAY+"When you rejoin, you will respawn as a fresh start!");

			UUID uuid = evt.getPlayer().getUniqueId();
			new BukkitRunnable(){
				int attempts = 0;
				@Override public void run(){
					//Make sure they're offline
					Player p = pl.getServer().getPlayer(uuid);
					if((p == null && pl.deletePlayerdata(uuid)) || ++attempts == 10) cancel();
				}
			}.runTaskTimer(pl, 5, 20);
		}
	}
}
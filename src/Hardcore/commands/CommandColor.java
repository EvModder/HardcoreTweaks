package Hardcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import Hardcore.HCTweaks;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.extras.TextUtils;

public class CommandColor extends EvCommand{
	public CommandColor(HCTweaks pl){super(pl);}

	@Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args){
		if(args.length > 0){
			ArrayList<String> tabCompletes = new ArrayList<String>();
			for(char ch : TextUtils.COLOR_CHARS) tabCompletes.add(String.valueOf(ch));
			return tabCompletes;
		}
		return null;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]){
		if(sender instanceof Player == false){
			sender.sendMessage(ChatColor.RED+"This command can only be run by in-game players!");
			return true;
		}
		if(args.length == 0){
			sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
					"&00 &11 &22 &33 &44 &55 &66 &77 &88 &99 &aa &bb &cc &dd &ee &ff"));
			sender.sendMessage(ChatColor.GRAY+"/color #");
		}
		else if(args.length > 1 || (args[0]=args[0].trim().replaceAll("&", "")).length() > 1){
			sender.sendMessage(ChatColor.GRAY+"Please provide just a single character");
		}
		else{
			char colorCh = Character.toLowerCase(args[0].charAt(0));
			ChatColor color = ChatColor.getByChar(colorCh);
			if(color == null){
				sender.sendMessage(ChatColor.GRAY+"Unknown color '"+colorCh+"'");
			}
			else if(TextUtils.isFormat(colorCh)){
				sender.sendMessage(ChatColor.GRAY+"Please pick a color code");
			}
			else{
				String name = sender.getName();
				HCTweaks.getPlugin().runCommand("nick "+name+" &"+colorCh+name);
				sender.sendMessage(color+"Color set!");
				((Player)sender).addScoreboardTag("color_nick");
			}
		}
		return true;
	}
}
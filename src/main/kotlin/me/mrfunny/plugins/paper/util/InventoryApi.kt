package me.mrfunny.plugins.paper.util

import me.mrfunny.plugins.paper.BedWars.Companion.colorize
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

object InventoryApi {
    fun clearInventoryExceptArmor(player: Player) {
        val armorContents = player.inventory.armorContents.clone() //Clone instance of ItemStack[]
        player.inventory.clear() //Clear inventory
        player.inventory.setArmorContents(armorContents) //Set armor using the clone instance.
        player.updateInventory()
    }

    fun giveAllResourcesFromPlayerToPlayer(from: Player, to: Player){
        var ironCount = 0
        var goldCount = 0
        var rubyCount = 0
        for(item in from.inventory){
            if(item == null) continue
            when(item.type){
                Material.GHAST_TEAR -> {
                    ironCount += item.amount
                    to.inventory.addItem(item)
                }
                Material.GOLD_NUGGET -> {
                    goldCount += item.amount
                    to.inventory.addItem(item)
                }
                Material.FERMENTED_SPIDER_EYE -> {
                    rubyCount += item.amount
                    to.inventory.addItem(item)
                }
                else -> continue
            }
        }
        if(ironCount != 0){
            to.sendMessage("&7+$ironCount iron".colorize())
        }
        if(goldCount != 0){
            to.sendMessage("&6+$goldCount gold".colorize())
        }
        if(rubyCount != 0){
            to.sendMessage("&4+$rubyCount ruby".colorize())
        }
    }

    fun upgradeArmor(player: Player) {
        if (Objects.requireNonNull(player.inventory.boots)!!
                .getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL) == 3
        ) return
        if (player.inventory.boots!!.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL) == 3) return
    }
}
#!/usr/bin/env bash
set -u
mkdir -p srv && cd srv
curl -sSLo installer.jar https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.28/forge-1.20.1-47.4.28-installer.jar
java -jar installer.jar --installServer > install.log 2>&1 || { tail -50 install.log; exit 1; }
mkdir -p mods && cp ../ci/mods/*.jar mods/ && cp ../build/libs/iron_spell_grimoire-*.jar mods/
echo "eula=true" > eula.txt
printf 'online-mode=false\nlevel-seed=1213\nmax-tick-time=-1\nview-distance=3\nsimulation-distance=3\n' > server.properties
echo "-Xmx3G" > user_jvm_args.txt
mkfifo cmds; exec 3<>cmds
./run.sh nogui < cmds > server.log 2>&1 &
SRV=$!
for i in $(seq 1 600); do grep -q "Done (" server.log && break; grep -q "Crash report" server.log && break; sleep 1; done
send() { echo "$1" >&3; sleep "${2:-3}"; }
send "forceload add 160 160 256 256" 20
send "place structure iron_spell_grimoire:grimoire_tower 208 64 208" 20
send "summon minecraft:zombie 0 100 0" 2
send "execute as @e[type=zombie,limit=1] run data get entity @s Health" 2
send "setblock 0 150 0 iron_spell_grimoire:grimoire_altar" 2
send "locate structure iron_spell_grimoire:grimoire_tower" 10
send "stop" 5
for i in $(seq 1 120); do kill -0 $SRV 2>/dev/null || break; sleep 1; done
cd ..; mkdir -p ci-out
cp srv/logs/latest.log ci-out/latest.log 2>/dev/null || true
cp srv/crash-reports/* ci-out/ 2>/dev/null || true
grep -nE "ERROR|Exception|iron_spell_grimoire|Generated|Done \(|nearest" ci-out/latest.log | grep -v "irons_spellbooks:chests" | head -80 > ci-out/summary.txt || true

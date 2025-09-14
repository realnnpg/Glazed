//got some messages/inspiration from blackout. Why did i even make this?
package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.Formatting;

import java.util.Random;

public class AutoSex extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> moanmode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("Message Mode")
        .description("What kind of messages to send.")
        .defaultValue(Mode.Submissive)
        .build()
    );

    private final Setting<String> targetPlayer = sgGeneral.add(new StringSetting.Builder()
        .name("target-player")
        .description("Name of the player to send messages to.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> randomize = sgGeneral.add(new BoolSetting.Builder()
        .name("Randomize")
        .description("Adds random letters/numbers after each message.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> bypass = sgGeneral.add(new BoolSetting.Builder()
        .name("Bypass")
        .description("Uses obfuscated messages to evade filters.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> randomLength = sgGeneral.add(new IntSetting.Builder()
        .name("Random Length")
        .description("Length of random suffix (1-50 chars).")
        .defaultValue(5)
        .min(1)
        .max(50)
        .sliderRange(1, 50)
        .visible(randomize::get)
        .build()
    );

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("Delay")
        .description("Tick delay between messages.")
        .defaultValue(50)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("Chat Feedback")
        .description("Shows feedback in chat when messages are sent.")
        .defaultValue(true)
        .build()
    );

    public AutoSex() {
        super(GlazedAddon.CATEGORY, "AutoSex", "Why would you even consider this?");
    }

    public enum Mode {
        Dominant,
        Submissive,
    }

    private int lastNum;
    private double timer = 0;
    private static final String[] Submissive = new String[]{
        "fuck me harder daddy",
        "deeper! daddy deeper!",
        "Fuck yes your so big!",
        "I love your cock %s!",
        "Do not stop fucking my ass before i cum!",
        "Oh your so hard for me",
        "Want to widen my ass up %s?",
        "I love you daddy",
        "Make my bussy pop",
        "%s loves my bussy so much",
        "i made %s cum so hard with my tight bussy",
        "Your cock is so big and juicy daddy!",
        "Please fuck me as hard as you can",
        "im %s's personal femboy cumdumpster!",
        "Please shoot your hot load deep inside me daddy!",
        "I love how %s's dick feels inside of me!",
        "%s gets so hard when he sees my ass!",
        "%s really loves fucking my ass really hard!",
        "why wont u say the last message",
    };

    private static final String[] Dominant = new String[]{
        "Be a good boy for daddy",
        "I love pounding your ass %s!",
        "Give your bussy to daddy!",
        "I love how you drip pre-cum while i fuck your ass %s",
        "Slurp up and down my cock like a good boy",
        "Come and jump on daddy's cock %s",
        "I love how you look at me while you suck me off %s",
        "%s looks so cute when i fuck him",
        "%s's bussy is so incredibly tight!",
        "%s takes dick like the good boy he is",
        "I love how you shake your ass on my dick",
        "%s moans so cutely when i fuck his ass",
        "%s is the best cumdupster there is!",
        "%s is always horny and ready for his daddy's dick",
        "My dick gets rock hard every time i see %s",
        "why wont u say the last message",
    };

    // **BYPASS MODE MESSAGES (Obfuscated)**
    private static final String[] SubmissiveBypass = new String[]{
        "f*ck me harder d@ddy",
        "d33p3r! d@ddy d33p3r!",
        "F*ck y3s ur s0 b1g!",
        "1 l0v3 ur c0ck %s!",
        "D0n't st0p f*ck1ng my @ss b4 1 cum!",
        "0h ur s0 h@rd 4 me",
        "W@nn@ w1d3n my @ss up %s?",
        "1 l0v3 u d@ddy",
        "M@k3 my bussy p0p",
        "%s l0v3s my bussy s0 much",
        "1 m@d3 %s cum s0 h@rd w1th my t1ght bussy",
        "Ur c0ck 1s s0 b1g & juicy d@ddy!",
        "Pl3@s3 f*ck m3 @s h@rd @s u c@n",
        "1m %s's p3rs0n@l f3mb0y c*mdumpst3r!",
        "Pl3@s3 sh00t ur h0t l0@d d33p 1ns1d3 m3 d@ddy!",
        "1 l0v3 h0w %s's d1ck f33ls 1ns1d3 m3!",
        "%s g3ts s0 h@rd wh3n h3 s33s my @ss!",
        "%s r34lly l0v3s f*ck1ng my @ss r34lly h@rd!",
        "y w0nt u s@y th3 l@st m3ss@g3",
    };

    private static final String[] DominantBypass = new String[]{
        "B3 @ g00d b0y 4 d@ddy",
        "1 l0v3 p0und1ng ur @ss %s!",
        "G1v3 ur bussy 2 d@ddy!",
        "1 l0v3 h0w u dr1p pr3-c*m wh1l3 1 f*ck ur @ss %s",
        "Slurp up & d0wn my c0ck l1k3 @ g00d b0y",
        "C0m3 & jump 0n d@ddy's c0ck %s",
        "1 l0v3 h0w u l00k @t m3 wh1l3 u suck m3 0ff %s",
        "%s l00ks s0 cut3 wh3n 1 f*ck h1m",
        "%s's bussy 1s s0 1ncr3d1bly t1ght!",
        "%s t@k3s d1ck l1k3 th3 g00d b0y h3 1s",
        "1 l0v3 h0w u sh@k3 ur @ss 0n my d1ck",
        "%s m0@ns s0 cut3ly wh3n 1 f*ck h1s @ss",
        "%s 1s th3 b3st c*mdumpst3r th3r3 1s!",
        "%s 1s @lw@ys h0rny & r34dy 4 h1s d@ddy's d1ck",
        "My d1ck g3ts r0ck h@rd 3v3ry t1m3 1 s33 %s",
        "y w0nt u s@y th3 l@st m3ss@g3",
    };

    private final Random r = new Random();

    @EventHandler
    private void onRender(Render3DEvent event) {
        timer = Math.min(delay.get(), timer + event.frameTime);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer >= delay.get()) {
            MOAN();
            timer = 0;
        } else {
            timer++;
        }
    }

    private void MOAN() {
        if (mc.player == null || targetPlayer.get().isEmpty()) {
            if (chatFeedback.get()) error("No target player set or player is null!");
            return;
        }

        String name = targetPlayer.get();
        String[] messages = getSelectedMessages();

        int num = getRandomMessageIndex(messages);
        String message = formatMessage(messages[num], name);

        if (chatFeedback.get()) {
            info("Sending to " + Formatting.GREEN + name + Formatting.RESET + ": " + message);
        }

        ChatUtils.sendPlayerMsg("/msg " + name + " " + message);
    }

    private String[] getSelectedMessages() {
        return bypass.get() ?
            (moanmode.get() == Mode.Submissive ? SubmissiveBypass : DominantBypass) :
            (moanmode.get() == Mode.Submissive ? Submissive : Dominant);
    }

    private int getRandomMessageIndex(String[] messages) {
        if (messages.length == 1) return 0;

        int num;
        do {
            num = r.nextInt(messages.length);
        } while (num == lastNum);

        lastNum = num;
        return num;
    }

    private String formatMessage(String message, String name) {
        message = message.replace("%s", name);
        if (randomize.get()) {
            message += generateRandomSuffix(randomLength.get());
        }
        return message;
    }

    private String generateRandomSuffix(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }
}

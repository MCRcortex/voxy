package me.cortex.neovoxy.client.config;

import me.cortex.neovoxy.common.util.Pair;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.*;
import net.caffeinemc.mods.sodium.api.config.structure.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

public class SodiumConfigBuilder {

    private static record Enabler(Predicate<ConfigState> tester, Identifier[] dependencies) {
        public Enabler(Predicate<ConfigState> tester, String[] dependencies) {
            this(tester, mapIds(dependencies));
        }
    }

    public abstract static class Enableable <TYPE extends Enableable<TYPE>> {
        private @Nullable Enabler prevEnabler;
        protected @Nullable Enabler enabler;

        private TYPE setEnabler0(Enabler enabler) {
            this.prevEnabler = this.enabler;
            this.enabler = enabler;
            {
                var children = this.getEnablerChildren();
                if (children != null) {
                    for (var child : children) {
                        if (child.enabler == null || child.enabler == this.prevEnabler) {
                            child.setEnabler0(this.enabler);
                        }
                    }
                }
            }

            return (TYPE) this;
        }

        public TYPE setEnabler(Predicate<ConfigState> enabler, String... dependencies) {
            return this.setEnabler0(new Enabler(enabler, dependencies));
        }

        public TYPE setEnabler(String enabler) {
            if (enabler == null) {
                return this.setEnabler(s->true);
            }
            var id = ResourceLocation.parse(enabler);
            return this.setEnabler(s->s.readBooleanOption(id), enabler);
        }

        public TYPE setEnablerAND(String... enablers) {
            var enablersId = mapIds(enablers);
            return this.setEnabler0(new Enabler(s->{
                for (var id : enablersId) {
                    if (!s.readBooleanOption(id)) {
                        return false;
                    }
                }
                return true;
            }, enablersId));
        }

        protected Enableable[] getEnablerChildren() {
            return null;
        }
    }

    public static class Page extends Enableable<Page> {
        protected Component name;
        protected Group[] groups;
        public Page(Component name, Group... groups) {
            this.name = name;
            this.groups = groups;
        }

        protected OptionPageBuilder create(ConfigBuilder builder, BuildCtx ctx) {
            var page = builder.createOptionPage();
            page.setName(this.name);
            for (var group : this.groups) {
                page.addOptionGroup(group.create(builder, ctx));
            }
            return page;
        }

        @Override
        protected Enableable[] getEnablerChildren() {
            return this.groups;
        }
    }

    public static class Group extends Enableable<Group> {
        protected Option[] options;
        public Group(Option... options) {
            this.options = options;
        }

        protected OptionGroupBuilder create(ConfigBuilder builder, BuildCtx ctx) {
            var group = builder.createOptionGroup();
            for (var option : this.options) {
                group.addOption(option.create(builder, ctx));
            }
            return group;
        }

        @Override
        protected Enableable[] getEnablerChildren() {
            return this.options;
        }
    }

    public static abstract class Option <TYPE, OPTION extends Option<TYPE,OPTION,STYPE>, STYPE extends StatefulOptionBuilder<TYPE>> extends Enableable<Option<TYPE,OPTION,STYPE>> {
        //Setter returns a post save update set
        protected String id;
        protected Component name;
        protected Component tooltip;
        protected Supplier<TYPE> getter;
        protected Consumer<TYPE> setter;
        public Option(String id, Component name, Component tooltip, Supplier<TYPE> getter, Consumer<TYPE> setter) {
            this.id = id;
            this.name = name;
            this.tooltip = tooltip;
            this.getter = getter;
            this.setter = setter;
        }

        public Option(String id, Component name, Supplier<TYPE> getter, Consumer<TYPE> setter) {
            this.id = id;
            this.name = name;
            this.getter = getter;
            this.setter = setter;
            if (name.getContents() instanceof TranslatableContents tc) {
                this.tooltip = Component.translatable(tc.getKey() + ".tooltip");
            } else {
                this.tooltip = name;
            }
        }


        protected Consumer<TYPE> postRunner;
        protected Identifier[] postRunnerConflicts;
        protected Identifier[] postChangeFlags;
        public OPTION setPostChangeRunner(Consumer<TYPE> postRunner, String... dontRunIfChangedVars) {
            this.postRunner = postRunner;
            this.postRunnerConflicts = mapIds(dontRunIfChangedVars);
            return (OPTION) this;
        }

        public OPTION setPostChangeFlags(String... flags) {
            this.postChangeFlags = mapIds(flags);
            return (OPTION) this;
        }

        protected abstract STYPE createType(ConfigBuilder builder);

        protected STYPE create(ConfigBuilder builder, BuildCtx ctx) {
            var option = this.createType(builder);
            option.setName(this.name);
            option.setTooltip(this.tooltip);

            Set<Identifier> flags = new LinkedHashSet<>();
            if (this.postRunner != null) {
                var id = ResourceLocation.parse(this.id);
                var runner = this.postRunner;
                var getter = this.getter;
                ctx.postRunner.register(id, ()->runner.accept(getter.get()), this.postRunnerConflicts);
                flags.add(id);
            }

            if (this.postChangeFlags != null) {
                flags.addAll(List.of(this.postChangeFlags));
            }

            if (!flags.isEmpty()) {
                option.setFlags(flags.toArray(Identifier[]::new));
            }

            option.setBinding(this.setter, this.getter);
            if (this.enabler != null) {
                var pred = this.enabler.tester;
                option.setEnabledProvider(s->pred.test(s), this.enabler.dependencies);
            }

            option.setStorageHandler(ctx.saveHandler);

            option.setDefaultValue(this.getter.get());

            return option;
        }
    }

    public static class IntOption extends Option<Integer, IntOption, IntegerOptionBuilder> {
        protected Function<ConfigState, Range> rangeProvider;
        protected String[] rangeDependencies;
        protected ControlValueFormatter formatter = v->Component.literal(Integer.toString(v));

        public IntOption(String id, Component name, Component tooltip, Supplier<Integer> getter, Consumer<Integer> setter, Range range) {
            super(id, name, tooltip, getter, setter);
            this.rangeProvider = s->range;
        }

        public IntOption(String id, Component name, Supplier<Integer> getter, Consumer<Integer> setter, Range range) {
            super(id, name, getter, setter);
            this.rangeProvider = s->range;
        }

        public IntOption setFormatter(IntFunction<Component> formatter) {
            this.formatter = v->formatter.apply(v);
            return this;
        }

        @Override
        protected IntegerOptionBuilder createType(ConfigBuilder builder) {
            return builder.createIntegerOption(ResourceLocation.parse(this.id));
        }

        @Override
        protected IntegerOptionBuilder create(ConfigBuilder builder, BuildCtx ctx) {
            var option = super.create(builder, ctx);
            if (this.rangeDependencies == null || this.rangeDependencies.length == 0) {
                option.setRange(this.rangeProvider.apply(null));
            } else {
                option.setRangeProvider((Function<ConfigState, SteppedValidator>)(Object) this.rangeProvider, mapIds(this.rangeDependencies));
            }
            option.setValueFormatter(this.formatter);
            return option;
        }
    }

    public static class BoolOption extends Option<Boolean, BoolOption, BooleanOptionBuilder> {
        public BoolOption(String id, Component name, Component tooltip, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            super(id, name, tooltip, getter, setter);
        }

        public BoolOption(String id, Component name, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            super(id, name, getter, setter);
        }

        @Override
        protected BooleanOptionBuilder createType(ConfigBuilder builder) {
            return builder.createBooleanOption(ResourceLocation.parse(this.id));
        }
    }

    private static <F,T> T[] map(F[] from, Function<F,T> mapper, Function<Integer,T[]> factory) {
        T[] arr = factory.apply(from.length);
        for (int i = 0; i < from.length; i++) {
            arr[i] = mapper.apply(from[i]);
        }
        return arr;
    }

    private static Identifier[] mapIds(String[] strings) {
        return map(strings, Identifier::parse, Identifier[]::new);
    }


    public static class PostApplyOps implements FlagHook {
        private record Hook(Identifier name, Runnable runnable, Set<Identifier> conflicts) {}
        private Map<Identifier, Hook> hooks = new LinkedHashMap<>();

        public PostApplyOps register(String name, Runnable postRunner, String... conflicts) {
            return this.register(ResourceLocation.parse(name), postRunner, mapIds(conflicts));
        }

        public PostApplyOps register(Identifier name, Runnable postRunner, Identifier... conflicts) {
            this.hooks.put(name, new Hook(name, postRunner, new LinkedHashSet<>(List.of(conflicts))));
            return this;
        }

        protected PostApplyOps build() {
            boolean changed = false;
            do {
                changed = false;
                for (var hook : this.hooks.values()) {
                    for (var ref : new LinkedHashSet<>(hook.conflicts)) {
                        var other = this.hooks.getOrDefault(ref, null);
                        if (other != null) {
                            changed |= hook.conflicts.addAll(other.conflicts);
                        }
                    }
                }
            } while (changed);

            return this;
        }

        @Override
        public Collection<Identifier> getTriggers() {
            return this.hooks.keySet();
        }

        @Override
        public void accept(Collection<Identifier> identifiers, ConfigState configState) {
            for (var id : identifiers) {
                var hook = this.hooks.get(id);
                if (hook != null) {
                    if (Collections.disjoint(identifiers, hook.conflicts)) {
                        hook.runnable.run();
                    }
                }
            }
        }
    }


    private static final class BuildCtx {
        public PostApplyOps postRunner = new PostApplyOps();
        public StorageEventHandler saveHandler;
    }

    public static void buildToSodium(ConfigBuilder builder, ModOptionsBuilder options, StorageEventHandler saveHandler, Consumer<PostApplyOps> registerOps, Page... pages) {
        var ctx = new BuildCtx();
        registerOps.accept(ctx.postRunner);
        ctx.saveHandler = saveHandler;
        for (var page : pages) {
            options.addPage(page.create(builder, ctx));
        }
        options.registerFlagHook(ctx.postRunner.build());
    }
}

package net.lenni0451.noteblockbot.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.lenni0451.noteblockbot.commands.annotations.Arg;
import net.lenni0451.noteblockbot.commands.annotations.Command;
import net.lenni0451.noteblockbot.commands.annotations.RateLimited;
import net.lenni0451.noteblockbot.commands.annotations.Required;
import net.lenni0451.noteblockbot.data.RateLimiter;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

@Slf4j
public abstract class CommandParser extends ListenerAdapter {

    private final List<RegisteredCommand> registeredCommands;

    public CommandParser() {
        this.registeredCommands = new ArrayList<>();
    }

    public void register(final CommandListUpdateAction commands) {
        for (Method method : this.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Command.class)) continue;
            if (Modifier.isStatic(method.getModifiers())) throw new IllegalArgumentException("Command methods must not be static");
            Command command = method.getDeclaredAnnotation(Command.class);
            RateLimited rateLimited = method.getDeclaredAnnotation(RateLimited.class);
            List<ArgumentType> arguments = new ArrayList<>();
            for (int i = 0; i < method.getParameterCount(); i++) {
                Class<?> type = method.getParameterTypes()[i];
                if (i == 0) {
                    if (!type.equals(SlashCommandInteractionEvent.class)) {
                        throw new IllegalArgumentException("First parameter must be of type SlashCommandInteractionEvent");
                    }
                } else {
                    Annotation[] annotations = method.getParameterAnnotations()[i];
                    arguments.add(this.getArgumentType(type, annotations));
                }
            }

            SlashCommandData commandData = Commands.slash(command.name(), command.description());
            for (ArgumentType argument : arguments) {
                commandData.addOption(argument.arg.type(), argument.arg.name(), argument.arg.description(), argument.required, argument.completionSupplier != null);
            }
            commandData.setGuildOnly(true).setDefaultPermissions(DefaultMemberPermissions.enabledFor(command.permissions()));
            commands.addCommands(commandData);

            method.setAccessible(true);
            this.registeredCommands.add(new RegisteredCommand(command, rateLimited, method, arguments));
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) return;
        for (RegisteredCommand command : this.registeredCommands) {
            if (!command.command().name().equalsIgnoreCase(event.getName())) continue;
            if (command.rateLimited.user() && !RateLimiter.tryUser(event.getUser().getIdLong())) {
                event.reply("You are sending too many requests. Please wait a bit before sending another request. 🐌").setEphemeral(true).queue();
            } else if (command.rateLimited.guild() && !RateLimiter.tryGuild(event.getGuild().getIdLong())) {
                event.reply("You are sending too many requests. Please wait a bit before sending another request. 🐌").setEphemeral(true).queue();
            } else {
                Object[] arguments = new Object[command.arguments.size() + 1];
                arguments[0] = event;
                for (int i = 0; i < command.arguments.size(); i++) {
                    try {
                        ArgumentType argumentType = command.arguments.get(i);
                        arguments[i + 1] = event.getOption(argumentType.arg.name(), argumentType.parser);
                    } catch (MessageException e) {
                        event.reply(e.getMessage()).setEphemeral(true).queue();
                        return;
                    } catch (Throwable t) {
                        log.error("An error occurred while parsing the arguments", t);
                        event.reply("Invalid argument: " + t.getMessage()).setEphemeral(true).queue();
                        return;
                    }
                }
                try {
                    try {
                        command.method.invoke(this, arguments);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    } catch (IllegalArgumentException e) {
                        if (e.getMessage().equalsIgnoreCase("argument type mismatch")) {
                            log.error("Argument type mismatch!");
                            log.error("Method arguments: {}", Arrays.stream(command.method.getParameterTypes()).map(Class::getName).toList());
                            log.error("Provided arguments: {}", Arrays.stream(arguments).map((Object o) -> o == null ? "null" : o.getClass().getName()).toList());
                        }
                        throw e;
                    }
                } catch (MessageException e) {
                    event.reply(e.getMessage()).setEphemeral(true).queue();
                    return;
                } catch (Throwable t) {
                    log.error("An error occurred while executing the command", t);
                    event.reply("An error occurred while executing the command: " + t.getMessage()).setEphemeral(true).queue();
                    return;
                }
            }
            break;
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (!event.isFromGuild()) return;
        for (RegisteredCommand command : this.registeredCommands) {
            if (!command.command().name().equalsIgnoreCase(event.getName())) continue;
            for (ArgumentType argument : command.arguments) {
                if (!argument.arg().name().equalsIgnoreCase(event.getFocusedOption().getName())) continue;
                if (argument.completionSupplier == null) return;
                List<String> completions = new ArrayList<>();
                argument.completionSupplier.complete(completions);
                event.replyChoices(
                        completions.stream()
                                .filter(c -> c.toLowerCase(Locale.ROOT).startsWith(event.getFocusedOption().getValue().toLowerCase(Locale.ROOT)))
                                .map(c -> new net.dv8tion.jda.api.interactions.commands.Command.Choice(c, c))
                                .toList()
                ).queue();
                return;
            }
        }
    }

    protected final void validateAttachment(final Message.Attachment attachment, final int maxFileSize, final String... allowedExtensions) {
        if (attachment.getFileExtension() == null || !Arrays.asList(allowedExtensions).contains(attachment.getFileExtension().toLowerCase(Locale.ROOT))) {
            throw new MessageException("The attachment is not a valid. Allowed extensions: " + String.join(", ", allowedExtensions));
        }
        if (attachment.getSize() > maxFileSize) {
            throw new MessageException("The attachment is too large (Max: " + maxFileSize + " bytes)");
        }
    }

    private ArgumentType getArgumentType(final Class<?> type, final Annotation[] annotations) {
        Arg arg = null;
        Required required = null;
        for (Annotation annotation : annotations) {
            if (annotation instanceof Arg) arg = (Arg) annotation;
            if (annotation instanceof Required) required = (Required) annotation;
        }
        if (arg == null) throw new IllegalArgumentException("Argument does not have a Arg annotation");
        ArgumentParser parser;
        CompletionSupplier completionSupplier = null;
        switch (arg.type()) {
            case STRING -> {
                if (type.equals(String.class)) {
                    parser = OptionMapping::getAsString;
                } else if (Enum.class.isAssignableFrom(type)) {
                    parser = optionMapping -> {
                        String value = optionMapping.getAsString();
                        try {
                            return Enum.valueOf((Class<? extends Enum>) type, value.toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException e) {
                            throw new MessageException("Unknown enum value: " + value);
                        }
                    };
                    completionSupplier = completions -> {
                        for (Enum<?> enumConstant : (Enum<?>[]) type.getEnumConstants()) {
                            completions.add(enumConstant.name().toLowerCase(Locale.ROOT));
                        }
                    };
                } else {
                    throw new IllegalArgumentException("Invalid argument type for STRING: " + type.getName());
                }
            }
            case INTEGER -> {
                if (type.equals(Integer.class)) {
                    parser = OptionMapping::getAsInt;
                } else {
                    throw new IllegalArgumentException("Invalid argument type for INTEGER: " + type.getName());
                }
            }
            case ATTACHMENT -> {
                if (type.equals(Message.Attachment.class)) {
                    parser = OptionMapping::getAsAttachment;
                } else {
                    throw new IllegalArgumentException("Invalid argument type for ATTACHMENT: " + type.getName());
                }
            }
            case CHANNEL -> {
                if (type.equals(GuildChannelUnion.class)) {
                    parser = OptionMapping::getAsChannel;
                } else {
                    throw new IllegalArgumentException("Invalid argument type for CHANNEL: " + type.getName());
                }
            }
            default -> throw new IllegalArgumentException("Invalid argument type: " + arg.type());
        }
        return new ArgumentType(arg, required != null, parser, completionSupplier);
    }


    private record RegisteredCommand(Command command, RateLimited rateLimited, Method method, List<ArgumentType> arguments) {
    }

    private record ArgumentType(Arg arg, boolean required, ArgumentParser parser, CompletionSupplier completionSupplier) {
    }

    @FunctionalInterface
    private interface ArgumentParser extends Function<OptionMapping, Object> {
    }

    @FunctionalInterface
    private interface CompletionSupplier {
        void complete(final List<String> completions);
    }

    private static class MessageException extends RuntimeException {
        public MessageException(final String message) {
            super(message);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

}

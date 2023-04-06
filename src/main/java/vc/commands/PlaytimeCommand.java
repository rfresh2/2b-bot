package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.swagger.mojang_api.handler.ProfilesApi;
import vc.swagger.mojang_api.model.InlineResponse2001;
import vc.swagger.vc.handler.PlaytimeApi;
import vc.swagger.vc.model.PlaytimeResponse;
import vc.util.Validator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PlaytimeCommand implements SlashCommand {

    private final PlaytimeApi playtimeApi = new PlaytimeApi();
    private final ProfilesApi profilesApi = new ProfilesApi();

    @Override
    public String getName() {
        return "playtime";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        return event.deferReply().then(handleLookup(event));
    }

    private Mono<Message> handleLookup(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("username")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        Optional<String> uuidOptional = event.getOption("uuid")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        if (uuidOptional.isPresent()) {
            if (Validator.isUUID(uuidOptional.get())) {
                return resolvePlaytime(event, uuidOptional.map(UUID::fromString).get());
            } else {
                return error(event, "Invalid UUID format");
            }
        } else if (playerNameOptional.isPresent()) {
            if (Validator.isValidUsername(playerNameOptional.get())) {
                final Optional<UUID> uuid = getPlayerUUID(playerNameOptional.get());
                if (uuid.isPresent()) {
                    return resolvePlaytime(event, uuid.get());
                } else {
                    return error(event, "Unable to find player");
                }
            }
        }
        return event.createFollowup()
                .withContent("Username or UUID must be specified");
    }

    private Optional<UUID> getPlayerUUID(final String username) {
        List<InlineResponse2001> profileUuid = profilesApi.getProfileUuid(List.of(username));
        return profileUuid.stream().findFirst()
                .map(InlineResponse2001::getId)
                .map(s -> s.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"))
                .map(UUID::fromString);
    }

    private Mono<Message> error(ChatInputInteractionEvent event, final String message) {
        return event.createFollowup().withContent(message);
    }

    private Mono<Message> resolvePlaytime(ChatInputInteractionEvent event, final UUID uuid) {
        PlaytimeResponse playtime = playtimeApi.playtime(UUID.fromString("572e683c-888a-4a0d-bc10-5d9cfa76d892"));
        Integer playtimeSeconds = playtime.getPlaytimeSeconds();
        String durationStr = formatDuration(playtimeSeconds);
        return event.createFollowup()
                .withContent(durationStr);
    }

    private String formatDuration(long durationInSeconds) {
        var secondsInMinute = 60L;
        var secondsInHour = secondsInMinute * 60L;
        var secondsInDay = secondsInHour * 24L;
        var secondsInMonth = secondsInDay * 30L; // assuming 30 days per month

        var months = durationInSeconds / secondsInMonth;
        var days = (durationInSeconds % secondsInMonth) / secondsInDay;
        var hours = (durationInSeconds % secondsInDay) / secondsInHour;

        return months + " months, " + days + " days, " + hours + " hours";
    }
}

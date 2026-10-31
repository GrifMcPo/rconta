        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botHandler = new TelegramBotHandler(token, this, playerManager, commandLogger, logsCommand,
                    commandExecutor, punishmentManager, botBanManager, groupManager, accountLinkManager);
            botsApi.registerBot(botHandler);
            getLogger().info("Telegram-бот успешно зарегистрирован!");
        } catch (TelegramApiException e) {
            getLogger().severe("Ошибка при регистрации бота: " + e.getMessage());
            e.printStackTrace();
        }

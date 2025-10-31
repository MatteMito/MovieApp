"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppModule = void 0;
const common_1 = require("@nestjs/common");
const config_1 = require("@nestjs/config");
const typeorm_1 = require("@nestjs/typeorm");
const jwt_1 = require("@nestjs/jwt");
const axios_1 = require("@nestjs/axios");
const movie_entity_1 = require("./database/entities/movie.entity");
const user_entity_1 = require("./database/entities/user.entity");
const user_movie_entity_1 = require("./database/entities/user-movie.entity");
const list_entity_1 = require("./database/entities/list.entity");
const app_controller_1 = require("./app.controller");
const movies_module_1 = require("./modules/movies/movies.module");
const auth_module_1 = require("./modules/auth/auth.module");
const analytics_module_1 = require("./modules/analytics/analytics.module");
const lists_module_1 = require("./modules/lists/lists.module");
const websocket_module_1 = require("./modules/websocket/websocket.module");
const database_module_1 = require("./database/database.module");
const tmdb_module_1 = require("./modules/tmdb/tmdb.module");
let AppModule = class AppModule {
};
exports.AppModule = AppModule;
exports.AppModule = AppModule = __decorate([
    (0, common_1.Module)({
        imports: [
            config_1.ConfigModule.forRoot({
                isGlobal: true,
                envFilePath: '.env',
            }),
            typeorm_1.TypeOrmModule.forRootAsync({
                imports: [config_1.ConfigModule],
                useFactory: (configService) => ({
                    type: 'postgres',
                    host: configService.get('DB_HOST', 'localhost'),
                    port: configService.get('DB_PORT', 5432),
                    username: configService.get('DB_USERNAME', 'postgres'),
                    password: configService.get('DB_PASSWORD', 'password'),
                    database: configService.get('DB_NAME', 'movieapp'),
                    entities: [
                        movie_entity_1.MovieEntity,
                        user_entity_1.UserEntity,
                        user_movie_entity_1.UserMovieEntity,
                        list_entity_1.MovieListEntity,
                    ],
                    synchronize: true,
                    logging: true,
                }),
                inject: [config_1.ConfigService],
            }),
            jwt_1.JwtModule.registerAsync({
                imports: [config_1.ConfigModule],
                useFactory: (configService) => ({
                    secret: configService.get('JWT_SECRET', 'movieapp-secret-key'),
                    signOptions: {
                        expiresIn: configService.get('JWT_EXPIRATION', '7d'),
                    },
                }),
                inject: [config_1.ConfigService],
                global: true,
            }),
            axios_1.HttpModule.register({
                timeout: 30000,
                maxRedirects: 5,
            }),
            database_module_1.DatabaseModule,
            movies_module_1.MoviesModule,
            auth_module_1.AuthModule,
            tmdb_module_1.TmdbModule,
            analytics_module_1.AnalyticsModule,
            lists_module_1.ListsModule,
            websocket_module_1.WebsocketModule,
        ],
        controllers: [app_controller_1.AppController],
    })
], AppModule);
//# sourceMappingURL=app.module.js.map
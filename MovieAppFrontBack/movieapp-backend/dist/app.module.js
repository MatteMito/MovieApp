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
const schedule_1 = require("@nestjs/schedule");
const database_module_1 = require("./database/database.module");
const movies_module_1 = require("./modules/movies/movies.module");
const tmdb_module_1 = require("./modules/tmdb/tmdb.module");
const auth_module_1 = require("./modules/auth/auth.module");
const lists_module_1 = require("./modules/lists/lists.module");
const websocket_module_1 = require("./modules/websocket/websocket.module");
const analytics_module_1 = require("./modules/analytics/analytics.module");
const app_controller_1 = require("./app.controller");
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
            typeorm_1.TypeOrmModule.forRoot({
                type: 'postgres',
                host: process.env.DB_HOST || 'localhost',
                port: parseInt(process.env.DB_PORT, 10) || 5432,
                username: process.env.DB_USER || 'postgres',
                password: process.env.DB_PASSWORD || 'postgres',
                database: process.env.DB_NAME || 'movieapp',
                entities: [__dirname + '/**/*.entity{.ts,.js}'],
                synchronize: false,
                logging: false,
            }),
            schedule_1.ScheduleModule.forRoot(),
            database_module_1.DatabaseModule,
            movies_module_1.MoviesModule,
            tmdb_module_1.TmdbModule,
            auth_module_1.AuthModule,
            lists_module_1.ListsModule,
            websocket_module_1.WebsocketModule,
            analytics_module_1.AnalyticsModule,
        ],
        controllers: [app_controller_1.AppController],
        providers: [],
    })
], AppModule);
//# sourceMappingURL=app.module.js.map
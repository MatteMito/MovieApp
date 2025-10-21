"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.MoviesModule = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const axios_1 = require("@nestjs/axios");
const config_1 = require("@nestjs/config");
const movies_controller_1 = require("./movies.controller");
const movies_service_1 = require("./movies.service");
const tmdb_service_1 = require("../tmdb/tmdb.service");
const movie_entity_1 = require("../../database/entities/movie.entity");
const tmdb_cache_entity_1 = require("../../database/entities/tmdb-cache.entity");
const database_module_1 = require("../../database/database.module");
const websocket_module_1 = require("../websocket/websocket.module");
let MoviesModule = class MoviesModule {
};
exports.MoviesModule = MoviesModule;
exports.MoviesModule = MoviesModule = __decorate([
    (0, common_1.Module)({
        imports: [
            typeorm_1.TypeOrmModule.forFeature([movie_entity_1.MovieEntity, tmdb_cache_entity_1.TmdbCacheEntity]),
            axios_1.HttpModule.register({
                timeout: 30000,
                maxRedirects: 5,
            }),
            config_1.ConfigModule,
            database_module_1.DatabaseModule,
            websocket_module_1.WebsocketModule,
        ],
        controllers: [movies_controller_1.MoviesController],
        providers: [movies_service_1.MoviesService, tmdb_service_1.TmdbService],
        exports: [movies_service_1.MoviesService, tmdb_service_1.TmdbService],
    })
], MoviesModule);
//# sourceMappingURL=movies.module.js.map
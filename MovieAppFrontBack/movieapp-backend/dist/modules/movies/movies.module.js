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
const movies_controller_1 = require("./movies.controller");
const movies_service_1 = require("./movies.service");
const user_movies_service_1 = require("./user-movies.service");
const movie_entity_1 = require("../../database/entities/movie.entity");
const user_movie_entity_1 = require("../../database/entities/user-movie.entity");
const tmdb_cache_entity_1 = require("../../database/entities/tmdb-cache.entity");
const database_module_1 = require("../../database/database.module");
const tmdb_module_1 = require("../tmdb/tmdb.module");
const websocket_module_1 = require("../websocket/websocket.module");
let MoviesModule = class MoviesModule {
};
exports.MoviesModule = MoviesModule;
exports.MoviesModule = MoviesModule = __decorate([
    (0, common_1.Module)({
        imports: [
            typeorm_1.TypeOrmModule.forFeature([
                movie_entity_1.MovieEntity,
                user_movie_entity_1.UserMovieEntity,
                tmdb_cache_entity_1.TmdbCacheEntity,
            ]),
            database_module_1.DatabaseModule,
            tmdb_module_1.TmdbModule,
            websocket_module_1.WebsocketModule,
        ],
        controllers: [movies_controller_1.MoviesController],
        providers: [
            movies_service_1.MoviesService,
            user_movies_service_1.UserMoviesService,
        ],
        exports: [
            movies_service_1.MoviesService,
            user_movies_service_1.UserMoviesService,
        ],
    })
], MoviesModule);
//# sourceMappingURL=movies.module.js.map
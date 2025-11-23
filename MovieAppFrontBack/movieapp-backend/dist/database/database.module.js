"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.DatabaseModule = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const database_service_1 = require("./database.service");
const movie_entity_1 = require("./entities/movie.entity");
const user_entity_1 = require("./entities/user.entity");
const user_movie_entity_1 = require("./entities/user-movie.entity");
let DatabaseModule = class DatabaseModule {
};
exports.DatabaseModule = DatabaseModule;
exports.DatabaseModule = DatabaseModule = __decorate([
    (0, common_1.Module)({
        imports: [
            typeorm_1.TypeOrmModule.forFeature([
                movie_entity_1.MovieEntity,
                user_entity_1.UserEntity,
                user_movie_entity_1.UserMovieEntity,
            ]),
        ],
        providers: [database_service_1.DatabaseService],
        exports: [database_service_1.DatabaseService, typeorm_1.TypeOrmModule],
    })
], DatabaseModule);
//# sourceMappingURL=database.module.js.map
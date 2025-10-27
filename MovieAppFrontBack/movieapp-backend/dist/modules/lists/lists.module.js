"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.ListsModule = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const lists_controller_1 = require("./lists.controller");
const lists_service_1 = require("./lists.service");
const list_entity_1 = require("../../database/entities/list.entity");
const movie_entity_1 = require("../../database/entities/movie.entity");
const user_entity_1 = require("../../database/entities/user.entity");
let ListsModule = class ListsModule {
};
exports.ListsModule = ListsModule;
exports.ListsModule = ListsModule = __decorate([
    (0, common_1.Module)({
        imports: [
            typeorm_1.TypeOrmModule.forFeature([
                list_entity_1.MovieListEntity,
                movie_entity_1.MovieEntity,
                user_entity_1.UserEntity,
            ]),
        ],
        controllers: [lists_controller_1.ListsController],
        providers: [lists_service_1.ListsService],
        exports: [lists_service_1.ListsService],
    })
], ListsModule);
//# sourceMappingURL=lists.module.js.map
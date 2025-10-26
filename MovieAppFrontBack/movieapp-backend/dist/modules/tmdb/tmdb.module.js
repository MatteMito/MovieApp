"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.TmdbModule = void 0;
const common_1 = require("@nestjs/common");
const axios_1 = require("@nestjs/axios");
const tmdb_service_1 = require("./tmdb.service");
const tmdb_controller_1 = require("./tmdb.controller");
const database_module_1 = require("../../database/database.module");
let TmdbModule = class TmdbModule {
};
exports.TmdbModule = TmdbModule;
exports.TmdbModule = TmdbModule = __decorate([
    (0, common_1.Module)({
        imports: [
            axios_1.HttpModule.register({
                timeout: 10000,
                maxRedirects: 5,
                headers: {
                    Accept: 'application/json',
                    'User-Agent': 'MovieApp/2.1 con Database PostgreSQL',
                },
            }),
            database_module_1.DatabaseModule,
        ],
        controllers: [tmdb_controller_1.TmdbController],
        providers: [tmdb_service_1.TmdbService],
        exports: [tmdb_service_1.TmdbService],
    })
], TmdbModule);
//# sourceMappingURL=tmdb.module.js.map
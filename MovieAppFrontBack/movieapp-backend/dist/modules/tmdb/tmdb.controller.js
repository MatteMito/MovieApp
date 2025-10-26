"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
var TmdbController_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.TmdbController = void 0;
const common_1 = require("@nestjs/common");
const tmdb_service_1 = require("./tmdb.service");
const database_service_1 = require("../../database/database.service");
let TmdbController = TmdbController_1 = class TmdbController {
    constructor(tmdbService, databaseService) {
        this.tmdbService = tmdbService;
        this.databaseService = databaseService;
        this.logger = new common_1.Logger(TmdbController_1.name);
    }
    async searchMovie(query, year) {
        try {
            if (!query) {
                throw new common_1.HttpException({ success: false, message: 'Query mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`🔍 Ricerca TMDB: ${query}${year ? ` (${year})` : ''}`);
            const yearNum = year ? parseInt(year, 10) : undefined;
            const tmdbMovie = await this.tmdbService.searchByTitle(query, yearNum);
            if (!tmdbMovie) {
                return {
                    success: false,
                    message: 'Nessun film trovato su TMDB',
                    timestamp: new Date().toISOString(),
                };
            }
            const movie = {
                title: tmdbMovie.title,
                year: tmdbMovie.release_date ? parseInt(tmdbMovie.release_date.substring(0, 4)) : undefined,
                tmdb_id: tmdbMovie.id,
                genres: tmdbMovie.genres?.map((g) => g.name) || [],
                director: tmdbMovie.credits?.crew?.find((c) => c.job === 'Director')?.name,
                actors: tmdbMovie.credits?.cast?.slice(0, 5).map((a) => a.name) || [],
                overview: tmdbMovie.overview,
                tagline: tmdbMovie.tagline,
                poster_url: tmdbMovie.poster_path
                    ? `https://image.tmdb.org/t/p/w500${tmdbMovie.poster_path}`
                    : undefined,
                backdrop_url: tmdbMovie.backdrop_path
                    ? `https://image.tmdb.org/t/p/original${tmdbMovie.backdrop_path}`
                    : undefined,
                tmdb_rating: tmdbMovie.vote_average,
                vote_count: tmdbMovie.vote_count,
                runtime: tmdbMovie.runtime,
                imdb_id: tmdbMovie.imdb_id,
            };
            this.logger.log(`✅ Film trovato: ${movie.title} (TMDB ID: ${movie.tmdb_id})`);
            return {
                success: true,
                data: movie,
                message: 'Film trovato su TMDB',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore ricerca TMDB: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async addMovieFromTmdb(body) {
        try {
            const { tmdb_id, user_id, status = 'watchlist' } = body;
            if (!tmdb_id || !user_id) {
                throw new common_1.HttpException({ success: false, message: 'tmdb_id e user_id richiesti', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`📥 Aggiunta film da TMDB: ${tmdb_id} per utente ${user_id}`);
            const tmdbMovie = await this.tmdbService['getMovieDetails'](tmdb_id);
            if (!tmdbMovie) {
                throw new Error('Film non trovato su TMDB');
            }
            const movie = {
                id: `tmdb_${tmdb_id}`,
                title: tmdbMovie.title,
                year: tmdbMovie.release_date ? parseInt(tmdbMovie.release_date.substring(0, 4)) : 0,
                source: 'tmdb',
                tmdb_id: tmdbMovie.id,
                genres: tmdbMovie.genres?.map((g) => g.name) || [],
                director: tmdbMovie.credits?.crew?.find((c) => c.job === 'Director')?.name,
                actors: tmdbMovie.credits?.cast?.slice(0, 5).map((a) => a.name) || [],
                overview: tmdbMovie.overview,
                tagline: tmdbMovie.tagline,
                poster_url: tmdbMovie.poster_path
                    ? `https://image.tmdb.org/t/p/w500${tmdbMovie.poster_path}`
                    : undefined,
                backdrop_url: tmdbMovie.backdrop_path
                    ? `https://image.tmdb.org/t/p/original${tmdbMovie.backdrop_path}`
                    : undefined,
                tmdb_rating: tmdbMovie.vote_average,
                vote_count: tmdbMovie.vote_count,
                runtime: tmdbMovie.runtime,
                budget: tmdbMovie.budget,
                revenue: tmdbMovie.revenue,
                status: tmdbMovie.status,
                original_language: tmdbMovie.original_language,
                original_title: tmdbMovie.original_title,
                popularity: tmdbMovie.popularity,
                adult: tmdbMovie.adult,
                homepage: tmdbMovie.homepage,
                imdb_id: tmdbMovie.imdb_id,
                production_companies: tmdbMovie.production_companies?.map((c) => c.name) || [],
                production_countries: tmdbMovie.production_countries?.map((c) => c.name) || [],
                spoken_languages: tmdbMovie.spoken_languages?.map((l) => l.name) || [],
                keywords: tmdbMovie.keywords?.keywords?.slice(0, 10).map((k) => k.name) || [],
                certification: tmdbMovie.releases?.countries?.find((c) => c.iso_3166_1 === 'US')?.certification,
                trailer_url: tmdbMovie.videos?.results?.find((v) => v.type === 'Trailer')?.key
                    ? `https://www.youtube.com/watch?v=${tmdbMovie.videos.results.find((v) => v.type === 'Trailer')?.key}`
                    : undefined,
            };
            const savedMovie = await this.databaseService.saveMovie(movie);
            this.logger.log(`✅ Film salvato nel database: ${savedMovie.id}`);
            return {
                success: true,
                data: savedMovie,
                message: 'Film aggiunto al database',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore aggiunta film: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
};
exports.TmdbController = TmdbController;
__decorate([
    (0, common_1.Get)('search'),
    __param(0, (0, common_1.Query)('query')),
    __param(1, (0, common_1.Query)('year')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], TmdbController.prototype, "searchMovie", null);
__decorate([
    (0, common_1.Post)('add-to-database'),
    __param(0, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [Object]),
    __metadata("design:returntype", Promise)
], TmdbController.prototype, "addMovieFromTmdb", null);
exports.TmdbController = TmdbController = TmdbController_1 = __decorate([
    (0, common_1.Controller)('api/v1/tmdb'),
    __metadata("design:paramtypes", [tmdb_service_1.TmdbService,
        database_service_1.DatabaseService])
], TmdbController);
//# sourceMappingURL=tmdb.controller.js.map
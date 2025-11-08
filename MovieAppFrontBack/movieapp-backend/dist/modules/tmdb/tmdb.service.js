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
var TmdbService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.TmdbService = void 0;
const common_1 = require("@nestjs/common");
const axios_1 = require("@nestjs/axios");
const config_1 = require("@nestjs/config");
const rxjs_1 = require("rxjs");
const database_service_1 = require("../../database/database.service");
let TmdbService = TmdbService_1 = class TmdbService {
    constructor(httpService, configService, databaseService) {
        this.httpService = httpService;
        this.configService = configService;
        this.databaseService = databaseService;
        this.logger = new common_1.Logger(TmdbService_1.name);
        this.baseUrl = 'https://api.themoviedb.org/3';
        this.maxRequestsPerWindow = 35;
        this.rateLimitWindow = 10000;
        this.requestHistory = [];
        this.apiKey = this.configService.get('TMDB_API_KEY');
    }
    async enrichMovie(movie) {
        try {
            await this.enforceRateLimit();
            const searchResult = await this.searchByTitle(movie.title, movie.year);
            if (!searchResult || !searchResult.id) {
                this.logger.warn(`film non trovato su tmdb: ${movie.title}`);
                return null;
            }
            await this.enforceRateLimit();
            const detailsUrl = `${this.baseUrl}/movie/${searchResult.id}`;
            const detailsResponse = await (0, rxjs_1.firstValueFrom)(this.httpService.get(detailsUrl, {
                params: {
                    api_key: this.apiKey,
                    language: 'it-IT',
                    append_to_response: 'credits,keywords,videos,release_dates',
                },
            }));
            const tmdbData = detailsResponse.data;
            const director = tmdbData.credits?.crew
                ?.find((c) => c.job === 'Director')?.name;
            const actors = tmdbData.credits?.cast
                ?.slice(0, 10)
                .map((a) => a.name) || [];
            const keywords = tmdbData.keywords?.keywords
                ?.slice(0, 10)
                .map((k) => k.name) || [];
            const certification = this.extractCertification(tmdbData);
            const trailerUrl = this.extractTrailerUrl(tmdbData);
            const enrichedMovie = {
                ...movie,
                tmdb_id: tmdbData.id,
                is_enriched: true,
                genres: tmdbData.genres?.map((g) => g.name) || [],
                director,
                actors,
                overview: tmdbData.overview,
                tagline: tmdbData.tagline,
                runtime: tmdbData.runtime,
                tmdb_rating: tmdbData.vote_average,
                vote_count: tmdbData.vote_count,
                popularity: tmdbData.popularity,
                budget: tmdbData.budget,
                revenue: tmdbData.revenue,
                poster_url: tmdbData.poster_path
                    ? `https://image.tmdb.org/t/p/w500${tmdbData.poster_path}`
                    : undefined,
                backdrop_url: tmdbData.backdrop_path
                    ? `https://image.tmdb.org/t/p/original${tmdbData.backdrop_path}`
                    : undefined,
                status: tmdbData.status,
                production_companies: tmdbData.production_companies?.map((c) => c.name) || [],
                production_countries: tmdbData.production_countries?.map((c) => c.name) || [],
                original_language: tmdbData.original_language,
                original_title: tmdbData.original_title,
                spoken_languages: tmdbData.spoken_languages?.map((l) => l.english_name) || [],
                adult: tmdbData.adult,
                homepage: tmdbData.homepage,
                imdb_id: tmdbData.imdb_id,
                keywords,
                certification,
                trailer_url: trailerUrl,
            };
            this.logger.log(`film arricchito: ${enrichedMovie.title} (tmdb_id: ${enrichedMovie.tmdb_id})`);
            return enrichedMovie;
        }
        catch (error) {
            this.logger.error(`errore enrichment ${movie.title}: ${error.message}`);
            return null;
        }
    }
    async searchByTitle(title, year) {
        try {
            const url = `${this.baseUrl}/search/movie`;
            const response = await (0, rxjs_1.firstValueFrom)(this.httpService.get(url, {
                params: {
                    api_key: this.apiKey,
                    query: title,
                    year: year,
                    language: 'it-IT',
                    include_adult: false,
                },
            }));
            if (!response.data.results || response.data.results.length === 0) {
                return null;
            }
            return response.data.results[0];
        }
        catch (error) {
            this.logger.error(`errore ricerca tmdb: ${error.message}`);
            return null;
        }
    }
    async syncPopularMovies(limit = 10000) {
        this.logger.log(`sync ${limit} film popolari da tmdb...`);
        let synced = 0;
        let errors = 0;
        const batchSize = 20;
        const totalPages = Math.ceil(limit / batchSize);
        for (let page = 1; page <= totalPages; page++) {
            try {
                await this.enforceRateLimit();
                const url = `${this.baseUrl}/movie/popular`;
                const response = await (0, rxjs_1.firstValueFrom)(this.httpService.get(url, {
                    params: {
                        api_key: this.apiKey,
                        language: 'it-IT',
                        page: page,
                    },
                }));
                if (!response.data.results || response.data.results.length === 0) {
                    break;
                }
                for (const tmdbMovie of response.data.results) {
                    try {
                        const existing = await this.databaseService.findMovieByTmdbId(tmdbMovie.id);
                        if (existing)
                            continue;
                        const movie = {
                            id: `tmdb_${tmdbMovie.id}`,
                            title: tmdbMovie.title,
                            year: tmdbMovie.release_date
                                ? new Date(tmdbMovie.release_date).getFullYear()
                                : undefined,
                            source: 'TMDB',
                            tmdb_id: tmdbMovie.id,
                            overview: tmdbMovie.overview,
                            poster_url: tmdbMovie.poster_path
                                ? `https://image.tmdb.org/t/p/w500${tmdbMovie.poster_path}`
                                : undefined,
                            popularity: tmdbMovie.popularity,
                            tmdb_rating: tmdbMovie.vote_average,
                            vote_count: tmdbMovie.vote_count,
                            genres: [],
                            actors: [],
                            is_enriched: false,
                        };
                        await this.databaseService.saveMovie(movie);
                        synced++;
                        if (synced % 100 === 0) {
                            this.logger.log(`${synced} film sincronizzati...`);
                        }
                    }
                    catch (error) {
                        this.logger.error(`errore ${tmdbMovie.title}: ${error.message}`);
                        errors++;
                    }
                }
                await new Promise(resolve => setTimeout(resolve, 300));
            }
            catch (error) {
                this.logger.error(`errore pagina ${page}: ${error.message}`);
                errors++;
            }
        }
        this.logger.log(`sync completato: ${synced} film, ${errors} errori`);
        return { synced, errors };
    }
    async searchForAutocomplete(query, limit = 10) {
        try {
            return await this.databaseService.searchMoviesForAutocomplete(query, limit);
        }
        catch (error) {
            this.logger.error(`errore autocomplete: ${error.message}`);
            return [];
        }
    }
    async enforceRateLimit() {
        const now = Date.now();
        this.requestHistory = this.requestHistory.filter((timestamp) => now - timestamp < this.rateLimitWindow);
        if (this.requestHistory.length >= this.maxRequestsPerWindow) {
            const oldestRequest = this.requestHistory[0];
            const waitTime = this.rateLimitWindow - (now - oldestRequest) + 100;
            this.logger.debug(`rate limit raggiunto, attesa ${waitTime}ms`);
            await new Promise((resolve) => setTimeout(resolve, waitTime));
        }
        this.requestHistory.push(Date.now());
    }
    extractCertification(tmdbData) {
        try {
            const releases = tmdbData.release_dates?.results;
            if (!releases)
                return undefined;
            const usRelease = releases.find((r) => r.iso_3166_1 === 'US');
            const itRelease = releases.find((r) => r.iso_3166_1 === 'IT');
            const release = itRelease || usRelease;
            if (release && release.release_dates && release.release_dates.length > 0) {
                return release.release_dates[0].certification;
            }
            return undefined;
        }
        catch (error) {
            return undefined;
        }
    }
    extractTrailerUrl(tmdbData) {
        try {
            const videos = tmdbData.videos?.results;
            if (!videos || videos.length === 0)
                return undefined;
            const trailer = videos.find((v) => v.type === 'Trailer' && v.site === 'YouTube');
            if (trailer) {
                return `https://www.youtube.com/watch?v=${trailer.key}`;
            }
            return undefined;
        }
        catch (error) {
            return undefined;
        }
    }
};
exports.TmdbService = TmdbService;
exports.TmdbService = TmdbService = TmdbService_1 = __decorate([
    (0, common_1.Injectable)(),
    __metadata("design:paramtypes", [axios_1.HttpService,
        config_1.ConfigService,
        database_service_1.DatabaseService])
], TmdbService);
//# sourceMappingURL=tmdb.service.js.map
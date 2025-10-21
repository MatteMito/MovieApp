import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { DatabaseService } from '../../database/database.service';
import { Movie } from '../../common/interfaces/movie.interface';
export declare class TmdbService {
    private readonly httpService;
    private readonly configService;
    private readonly databaseService;
    private readonly logger;
    private readonly baseUrl;
    private readonly apiKey;
    private readonly rateLimitWindow;
    private readonly maxRequestsPerWindow;
    private requestHistory;
    constructor(httpService: HttpService, configService: ConfigService, databaseService: DatabaseService);
    enrichMovie(movie: Movie): Promise<Movie>;
    enrichMovies(movies: Movie[], options?: {
        onProgress?: (processed: number, total: number, currentMovie?: string) => Promise<void>;
    }): Promise<{
        successfulMovies: Movie[];
        failedMovies: Array<{
            movie: Movie;
            error: string;
        }>;
        totalProcessed: number;
        successRate: number;
    }>;
    private findByImdbIdWithCache;
    private searchByTitleWithCache;
    private getMovieDetailsWithCache;
    private enforceRateLimit;
    private recordApiCall;
    private generateTmdbCacheKey;
    private findBestMatch;
    private mapTmdbToMovie;
    private delay;
    healthCheck(): Promise<{
        status: string;
        details?: any;
    }>;
    getApiUsageStats(): any;
}

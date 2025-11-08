import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { DatabaseService } from '../../database/database.service';
import { Movie } from '../../common/interfaces/movie.interface';
export declare class TmdbService {
    private readonly httpService;
    private readonly configService;
    private readonly databaseService;
    private readonly logger;
    private readonly apiKey;
    private readonly baseUrl;
    private readonly maxRequestsPerWindow;
    private readonly rateLimitWindow;
    private requestHistory;
    constructor(httpService: HttpService, configService: ConfigService, databaseService: DatabaseService);
    enrichMovie(movie: Movie): Promise<Movie | null>;
    searchByTitle(title: string, year?: number): Promise<any>;
    syncPopularMovies(limit?: number): Promise<{
        synced: number;
        errors: number;
    }>;
    searchForAutocomplete(query: string, limit?: number): Promise<Movie[]>;
    private enforceRateLimit;
    private extractCertification;
    private extractTrailerUrl;
}

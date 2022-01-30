#include "CircuitReader.hpp"
#include <libsnark/gadgetlib2/integration.hpp>
#include <libsnark/gadgetlib2/adapters.hpp>
#include <libsnark/zk_proof_systems/ppzksnark/r1cs_ppzksnark/examples/run_r1cs_ppzksnark.hpp>
#include <libsnark/zk_proof_systems/ppzksnark/r1cs_ppzksnark/r1cs_ppzksnark.hpp>
#include <libsnark/zk_proof_systems/ppzksnark/r1cs_gg_ppzksnark/examples/run_r1cs_gg_ppzksnark.hpp>
#include <libsnark/zk_proof_systems/ppzksnark/r1cs_gg_ppzksnark/r1cs_gg_ppzksnark.hpp>
#include <libsnark/common/default_types/r1cs_gg_ppzksnark_pp.hpp>
#include <libff/algebra/curves/alt_bn128/alt_bn128_pp.hpp>


#include <fstream>
#include <iostream>
#include <cassert>
#include <iomanip>
#include <gmp.h>

#include "json.hpp"
using json = nlohmann::json;

typedef libff::default_ec_pp ppT;
typedef libff::Fq<ppT> FqT;
typedef libsnark::r1cs_gg_ppzksnark_proof<ppT> ProofT;
typedef libsnark::r1cs_gg_ppzksnark_verification_key<ppT> VerificationKeyT;
typedef libsnark::r1cs_gg_ppzksnark_proving_key<ppT> ProvingKeyT;
typedef libsnark::r1cs_gg_ppzksnark_primary_input<ppT> PrimaryInputT;
typedef libff::alt_bn128_G1 G1T;
typedef libff::alt_bn128_G2 G2T;

using libsnark::accumulation_vector;

template<typename T>
void writeToFile(std::string path, T& obj) {
    std::stringstream ss;
    ss << obj;
    std::ofstream fh;
    fh.open(path, std::ios::binary);
    ss.rdbuf()->pubseekpos(0, std::ios_base::out);
    fh << ss.rdbuf();
    fh.flush();
    fh.close();
}

template<typename T>
T loadFromFile(std::string path) {
    std::stringstream ss;
    std::ifstream fh(path, std::ios::binary);

    // TODO: more useful error if file not found
    assert(fh.is_open());

    ss << fh.rdbuf();
    fh.close();

    ss.rdbuf()->pubseekpos(0, std::ios_base::in);

    T obj;
    ss >> obj;

    return obj;
}

std::string HexStringFromBigint(libff::bigint<libff::alt_bn128_r_limbs> _x) {
    mpz_t value;
    ::mpz_init(value);

    _x.to_mpz(value);
    char *value_out_hex = mpz_get_str(nullptr, 16, value);

    std::string str(value_out_hex);

    ::mpz_clear(value);
    ::free(value_out_hex);

    return str;
}

std::string outputPointG1AffineAsHex(G1T _p) {
        auto aff = _p;
        aff.to_affine_coordinates();
        return "\"0x" + HexStringFromBigint(aff.X.as_bigint()) + "\", \"0x" + HexStringFromBigint(aff.Y.as_bigint()) + "\""; 
}

std::string outputPointG2AffineAsHex(G2T _p)
{
        G2T aff = _p;

        if (aff.Z.c0.as_bigint() != "0" && aff.Z.c1.as_bigint() != "0" ) {
            aff.to_affine_coordinates();
        }
        return "[\"0x" +
                HexStringFromBigint(aff.X.c1.as_bigint()) + "\", \"0x" +
                HexStringFromBigint(aff.X.c0.as_bigint()) + "\"],\n [\"0x" + 
                HexStringFromBigint(aff.Y.c1.as_bigint()) + "\", \"0x" +
                HexStringFromBigint(aff.Y.c0.as_bigint()) + "\"]"; 
}

std::string proof_to_json(ProofT &proof, const PrimaryInputT &input) {
    std::stringstream ss;

    ss << "{\n";
    ss << " \"A\" :[" << outputPointG1AffineAsHex(proof.g_A) << "],\n";
    ss << " \"B\"  :[" << outputPointG2AffineAsHex(proof.g_B)<< "],\n";
    ss << " \"C\"  :[" << outputPointG1AffineAsHex(proof.g_C)<< "],\n";
    ss << " \"input\" :" << "["; //1 should always be the first variavle passed

    for (size_t i = 0; i < input.size(); ++i)
    {   
        ss << "\"0x" << HexStringFromBigint(input[i].as_bigint()) << "\""; 
        if ( i < input.size() - 1 ) { 
            ss<< ", ";
        }
    }
    ss << "]\n";
    ss << "}";

    ss.rdbuf()->pubseekpos(0, std::ios_base::out);

    return(ss.str());
}

std::string vk2json(VerificationKeyT &vk) {
    std::stringstream ss;
    unsigned icLength = vk.gamma_ABC_g1.rest.indices.size() + 1;
    
    ss << "{\n";
    ss << " \"alpha\" :[" << outputPointG1AffineAsHex(vk.alpha_g1) << "],\n";
    ss << " \"beta\"  :[" << outputPointG2AffineAsHex(vk.beta_g2) << "],\n";
    ss << " \"gamma\" :[" << outputPointG2AffineAsHex(vk.gamma_g2) << "],\n";
    ss << " \"delta\" :[" << outputPointG2AffineAsHex(vk.delta_g2)<< "],\n";

    ss <<  "\"gammaABC\" :[[" << outputPointG1AffineAsHex(vk.gamma_ABC_g1.first) << "]";
    
    for (size_t i = 1; i < icLength; ++i)
    {   
        auto vkICi = outputPointG1AffineAsHex(vk.gamma_ABC_g1.rest.values[i - 1]);
        ss << ",[" <<  vkICi << "]";
    } 
    ss << "]";
    ss << "}";
    return ss.str();
}

void vk2json_file(VerificationKeyT &vk, const std::string &path) {
    std::ofstream fh;
    fh.open(path, std::ios::binary);
    fh << vk2json(vk);
    fh.flush();
    fh.close();
}

/**
* Loads a ppT::Fq_type from a string, allows for integer, hex or binary encoding
* Prefix with 0x for hex and 0b for binary
*/
template<typename T>
T parse_bigint(const std::string &input)
{
    mpz_t value;
    int value_error;

    ::mpz_init(value);

    // the '0' flag means auto-detect, e.g. '0x' or '0b' prefix for hex/binary
    value_error = ::mpz_set_str(value, input.c_str(), 0);
    if( value_error ) {
        throw std::invalid_argument("Invalid field element");
    }

    T out(value);
    ::mpz_clear(value);

    return out;
}

FqT parse_Fq(const string &input) {
    return parse_bigint<FqT>(input);
}


FieldT parse_FieldT(const string &input) {
    return parse_bigint<FieldT>(input);
}


/**
* Create a list of F<x> elements from a node in a property tree, in JSON this is:
*
*   [N, N, N, ...]
*/
vector<FieldT> create_F_list( const json &in_tree )
{
    vector<FieldT> elements;

    for( auto& item : in_tree )
    {
        elements.emplace_back( parse_FieldT( item ) );
    }

    return elements;
}


/**
* Create a G1 point from X and Y coords (integers or hex as strings)
*
* This assumes the coordinates are affine.
*/
G1T create_G1(const string &in_X, const string &in_Y)
{
    return G1T(parse_Fq(in_X), parse_Fq(in_Y), FqT("1"));

    // TODO: verify well_formed
}


/**
* Create a G2 point from 512bit big-endian X and Y coords (integers or hex as strings)
*
*   X.c1, X.c0, Y.c1, Y.c0
*
* This assumes the coordinates are affine.
*/
G2T create_G2(const string &in_X_c1, const string &in_X_c0, const string &in_Y_c1, const string &in_Y_c0)
{
    typedef typename ppT::Fqe_type Fq2_T;

    return G2T(
        Fq2_T(parse_Fq(in_X_c0), parse_Fq(in_X_c1)),
        Fq2_T(parse_Fq(in_Y_c0), parse_Fq(in_Y_c1)),
        Fq2_T(FqT("1"), FqT("0")));   // Z is hard-coded, coordinates are affine

    // TODO: verify well_formed
}


/**
* Create a G1 element from a node in a property tree, in JSON this is:
*
*   "in_key": ["X", "Y"]
*/
G1T create_G1( const json &in_tree )
{
    assert(in_tree.size() == 2);
    return create_G1(in_tree[0].get<string>(), in_tree[1].get<string>());
}


/**
* Create a list of G1 points from a node in a property tree, in JSON this is:
*
*   "in_key": [["X", "Y"], ["X", "Y"], ...]
*/
vector<G1T> create_G1_list( const json &in_tree )
{
    typedef typename ppT::G1_type G1_T;

    vector<G1_T> points;

    for( auto& item : in_tree )
    {
        points.emplace_back( create_G1(item) );
    }

    return points;
}



/**
* Create a G2 element from a node in a property tree, in JSON this is:
*
*   [["X.c1", "X.c0"], ["Y.c1", "Y.c0"]]
*/
G2T create_G2( const json &in_tree )
{
    assert( in_tree.size() == 2 );
    assert( in_tree[0].size() == 2 );
    assert( in_tree[1].size() == 2 );

    return create_G2(in_tree[0][0].get<string>(), in_tree[0][1].get<string>(),
                     in_tree[1][0].get<string>(), in_tree[1][1].get<string>());
}

/**
* Pair which represents a proof and its inputs
*/
using InputProofPairType = std::pair< PrimaryInputT, ProofT >;

/**
* Parse the witness/proof from a property tree
*   {"A": g1,
*    "B": g2,
*    "C": g1,
*    "input": [N, N, N ...]}
*/
InputProofPairType proof_from_json( const json &in_tree )
{
    auto A = create_G1(in_tree.at("A"));
    auto B = create_G2(in_tree.at("B"));
    auto C = create_G1(in_tree.at("C"));
    auto input = create_F_list(in_tree.at("input"));

    ProofT proof(
        std::move(A),
        std::move(B),
        std::move(C));

    return InputProofPairType(input, proof);
}


/**
* Parse the witness/proof from a stream of JSON encoded data
*/
InputProofPairType proof_from_json( stringstream &in_json ) {
    return proof_from_json(json::parse(in_json));
}


/**
* Parse the verification key from a property tree
*
*   {"alpha": g1,
*    "beta": g2,
*    "gamma": g2,
*    "delta": g2,
*    "gamma_ABC": [g1, g1, g1...]}
*/
VerificationKeyT vk_from_json( const json &in_tree )
{
    // Array of IC G1 points
    auto gamma_ABC_g1 = create_G1_list(in_tree.at("gammaABC"));
    auto alpha_g1 = create_G1(in_tree.at("alpha"));
    auto beta_g2 = create_G2(in_tree.at("beta"));
    auto gamma_g2 = create_G2(in_tree.at("gamma"));
    auto delta_g2 = create_G2(in_tree.at("delta"));

    // IC must be split into `first` and `rest` for the accumulator
    auto gamma_ABC_g1_rest = decltype(gamma_ABC_g1)(gamma_ABC_g1.begin() + 1, gamma_ABC_g1.end());
    auto gamma_ABC_g1_vec = accumulation_vector<G1T>(std::move(gamma_ABC_g1[0]), std::move(gamma_ABC_g1_rest));

    return VerificationKeyT(
        alpha_g1,
        beta_g2,
        gamma_g2,
        delta_g2,
        gamma_ABC_g1_vec);
}


/**
* Parse the verifying key from a stream of JSON encoded data
*/
VerificationKeyT vk_from_json( stringstream &in_json ) {
    return vk_from_json(json::parse(in_json));
}

bool stub_verify( const char *vk_json, const char *proof_json )
{
	gadgetlib2::initPublicParamsFromDefaultPp();

    std::stringstream vk_stream;
    vk_stream << vk_json;
    auto vk = vk_from_json(vk_stream);

    std::stringstream proof_stream;
    proof_stream << proof_json;
    auto proof_pair = proof_from_json(proof_stream);

    auto status = libsnark::r1cs_gg_ppzksnark_verifier_strong_IC <ppT> (vk, proof_pair.first, proof_pair.second);
    if( status )
        return true;

    return false;
}

int stub_main_verify(const std::string &vk_file, const std::string &proof_file) {
    // Read verifying key file
    std::stringstream vk_stream;
    std::ifstream vk_input(vk_file);
    if( ! vk_input ) {
        std::cerr << "Error: cannot open " << vk_file << std::endl;
        return 2;
    }
    vk_stream << vk_input.rdbuf();
    vk_input.close();

    // Read proof file
    std::stringstream proof_stream;
    std::ifstream proof_input(proof_file);
    if( ! proof_input ) {
        std::cerr << "Error: cannot open " << proof_file << std::endl;
        return 2;
    }
    proof_stream << proof_input.rdbuf();
    proof_input.close();

    // Then verify if proof is correct
    auto vk_str = vk_stream.str();
    auto proof_str = proof_stream.str();

    if( stub_verify( vk_str.c_str(), proof_str.c_str() ) )
    {
        return 0;
    }

    std::cerr << "Error: failed to verify proof!" << std::endl;

    return 1;
}


int main(int argc, char **argv) {

	libff::start_profiling();
	gadgetlib2::initPublicParamsFromDefaultPp();
	gadgetlib2::GadgetLibAdapter::resetVariableIndex();
	ProtoboardPtr pb = gadgetlib2::Protoboard::create(gadgetlib2::R1P);

	int inputStartIndex = 0;

	// Read the circuit, evaluate, and translate constraints
	CircuitReader reader(argv[1 + inputStartIndex], argv[2 + inputStartIndex], pb);
	r1cs_constraint_system<FieldT> cs = get_constraint_system_from_gadgetlib2(
			*pb);
	const r1cs_variable_assignment<FieldT> full_assignment =
			get_variable_assignment_from_gadgetlib2(*pb);
	cs.primary_input_size = reader.getNumInputs() + reader.getNumOutputs();
	cs.auxiliary_input_size = full_assignment.size() - cs.num_inputs();

	// extract primary and auxiliary input
	const r1cs_primary_input<FieldT> primary_input(full_assignment.begin(),
			full_assignment.begin() + cs.num_inputs());
	const r1cs_auxiliary_input<FieldT> auxiliary_input(
			full_assignment.begin() + cs.num_inputs(), full_assignment.end());


	// only print the circuit output values if both flags MONTGOMERY and BINARY outputs are off (see CMakeLists file)
	// In the default case, these flags should be ON for faster performance.

#if !defined(MONTGOMERY_OUTPUT) && !defined(OUTPUT_BINARY)
	cout << endl << "Printing output assignment in readable format:: " << endl;
	std::vector<Wire> outputList = reader.getOutputWireIds();
	int start = reader.getNumInputs();
	int end = reader.getNumInputs() +reader.getNumOutputs();	
	for (int i = start ; i < end; i++) {
		cout << "[output]" << " Value of Wire # " << outputList[i-reader.getNumInputs()] << " :: ";
		cout << primary_input[i];
		cout << endl;
	}
	cout << endl;
#endif

	// removed cs.is_valid() check due to a suspected (off by 1) issue in a newly added check in their method.
    // A follow-up will be added.
	if(!cs.is_satisfied(primary_input, auxiliary_input)){
		cout << "The constraint system is  not satisifed by the value assignment - Terminating." << endl;
		return -1;
	}

	// generate pk and vk
    cout << endl << "##### generating pk and vk #####" << endl;
	const char *pk_file = argv[3 + inputStartIndex];
	const char *vk_file = argv[4 + inputStartIndex];
	auto keypair = libsnark::r1cs_gg_ppzksnark_generator<ppT>(cs);
	vk2json_file(keypair.vk, vk_file);
	writeToFile<decltype(keypair.pk)>(pk_file, keypair.pk);

	// prove
    cout << endl << "##### computing proof with pk #####" << endl;
	const char *proof_file = argv[5 + inputStartIndex];
	auto proving_key = loadFromFile<ProvingKeyT>(pk_file);
	auto proof = libsnark::r1cs_gg_ppzksnark_prover<ppT>(proving_key, primary_input, auxiliary_input);
	auto json = proof_to_json(proof, primary_input);

    ofstream fh;
    fh.open(proof_file, std::ios::binary);
    fh << json;
    fh.flush();
    fh.close();

	// verify
    cout << endl << "##### verifying proof with vk #####" << endl;
	return stub_main_verify(vk_file, proof_file);

	return 0;
}

